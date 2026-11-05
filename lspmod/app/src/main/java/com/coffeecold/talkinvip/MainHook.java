package com.coffeecold.talkinvip;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Talkin 5.9.0 (com.talkin.learn) VIP hook — LSPosed 模块入口
 *
 * 核心 VIP 判定收敛在混淆后的单例管理类（方法名 j/i/k 各构建一致，
 * 类名随混淆批次变化：磁盘包为 j6c，Google Play 安装包为 ula）。
 * 结构签名：持有 VipDetailResp 字段 + 三个无参布尔方法 j()Z/i()Z/k()Z。
 *
 * 设备构建 (ula) 实体 getter 映射（dexdump 逐字节验证）：
 *   VipInfo:       e()=isVip, d()=wasVip, b()=hasTrailed
 *   VipDetailResp: e()=isVip, d()=wasVip, b()=getDetail(不 hook)
 *   VipDetail:     a()=expire_time, b()=has_trialed, d()=is_subscribed
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "TalkinVIP";
    private static final String TARGET_PKG = "com.talkin.learn";

    /** VIP 管理类候选（按混淆批次排列，逐个探测 j()Z 命中即用） */
    private static final String[] MANAGER_CANDIDATES = {"ula", "j6c", "v5c", "h56"};

    private static void log(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        if (!TARGET_PKG.equals(lpp.packageName)) {
            return;
        }
        log("loaded into " + lpp.packageName + " process=" + lpp.processName);

        try {
            installHooks(lpp.classLoader);
        } catch (Throwable t) {
            log("install failed: " + android.util.Log.getStackTraceString(t));
        }
    }

    private static void installHooks(ClassLoader cl) {
        // 1) VIP 管理类：探测候选类中拥有无参 j()Z 的那个
        Class<?> manager = null;
        for (String name : MANAGER_CANDIDATES) {
            try {
                Class<?> c = XposedHelpers.findClass(name, cl);
                Method j = c.getDeclaredMethod("j");
                if (j.getReturnType() == boolean.class) {
                    manager = c;
                    log("manager class = " + name);
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        if (manager == null) {
            throw new IllegalStateException("no manager class found among candidates");
        }

        // j() → true（全局 isVip 判定）
        hook(manager, "j", true);
        // i() → true（has_trialed）
        hook(manager, "i", true);
        // k() → true（was_vip）
        hook(manager, "k", true);

        // 2) 实体层 getter 兜底（混淆名按设备构建映射）
        hookEntity(cl, "com.talk.common.entity.response.VipInfo",
                new String[][] {{"e", "true"}, {"d", "true"}, {"b", "true"}});
        hookEntity(cl, "com.talk.common.entity.response.VipDetailResp",
                new String[][] {{"e", "true"}, {"d", "true"}});

        // 3) VipDetail：expire 拉远、has_trialed=false（试用入口常开）、is_subscribed=true
        try {
            Class<?> detail = XposedHelpers.findClass(
                    "com.talk.common.entity.response.VipDetailResp$VipDetail", cl);
            XposedHelpers.findAndHookMethod(detail, "a", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return 4102444800L; // 2100-01-01
                }
            });
            hook(detail, "b", false); // has_trialed
            hook(detail, "d", true);  // is_subscribed
        } catch (Throwable t) {
            log("VipDetail hooks skipped: " + t.getMessage());
        }

        // 4) 数据源回写：VipInfo 内部字段置 VIP 态
        try {
            final Class<?> vipInfo = XposedHelpers.findClass(
                    "com.talk.common.entity.response.VipInfo", cl);
            XposedHelpers.findAndHookMethod(vipInfo, "e", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    patchVipInfoFields(param.thisObject);
                    return Boolean.TRUE;
                }
            });
        } catch (Throwable t) {
            log("VipInfo patcher skipped: " + t.getMessage());
        }

        log("all hooks installed");
    }

    private static void hook(Class<?> clazz, String method, final boolean result) {
        try {
            XposedHelpers.findAndHookMethod(clazz, method, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return result;
                }
            });
        } catch (Throwable t) {
            log("hook " + clazz.getName() + "." + method + " skipped: " + t.getMessage());
        }
    }

    private static void hookEntity(ClassLoader cl, String className, String[][] methods) {
        try {
            Class<?> c = XposedHelpers.findClass(className, cl);
            for (String[] m : methods) {
                hook(c, m[0], Boolean.parseBoolean(m[1]));
            }
        } catch (Throwable t) {
            log("entity " + className + " skipped: " + t.getMessage());
        }
    }

    private static void patchVipInfoFields(Object vipInfo) {
        try {
            Class<?> c = vipInfo.getClass();
            // 字段 b=hasTrailed, c=isVip, d=wasVip, e=isSubscribed（设备构建声明序）
            setBooleanField(c, vipInfo, "c", true);
            setBooleanField(c, vipInfo, "d", true);
            setBooleanField(c, vipInfo, "b", true);
            setBooleanField(c, vipInfo, "e", true);
        } catch (Throwable t) {
            // 字段回写失败不影响 getter hook 兜底
        }
    }

    private static void setBooleanField(Class<?> c, Object obj, String name, boolean value)
            throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, Boolean.valueOf(value));
    }
}
