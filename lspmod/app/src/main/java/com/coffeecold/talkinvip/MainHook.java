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
 * Talkin 5.9.0 VIP hook — LSPosed 模块入口
 *
 * 全 app 的 VIP 判定收敛在 defpackage.j6c（gz6.j() 返回其单例）：
 *   j() → isVip 判定（43 处调用）
 *   i() → has_trialed（试用状态）
 *   k() → was_vip
 * 实体层兜底：VipInfo / VipDetailResp 的 getter。
 * VipInfo 内部字段直接回写，保证缓存/持久化数据本身也是 VIP 态。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "TalkinVIP";
    private static final String TARGET_PKG = "com.talkin.learn";

    private static void log(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        if (!TARGET_PKG.equals(lpp.packageName)) {
            return;
        }
        log("loaded into " + lpp.packageName + " process=" + lpp.processName);

        ClassLoader cl = lpp.classLoader;
        try {
            installHooks(cl);
            log("hooks installed");
        } catch (Throwable t) {
            log("install failed: " + XposedBridge.stackTraceToString(t));
        }
    }

    private static void installHooks(ClassLoader cl) throws Throwable {
        final Class<?> j6c = XposedHelpers.findClass("defpackage.j6c", cl);

        // 1) j6c.j() → true（全局 VIP 判定）
        XposedHelpers.findAndHookMethod(j6c, "j", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return true;
            }
        });

        // 2) j6c.i() → true（has_trialed：从未试用 → 试用入口常开）
        XposedHelpers.findAndHookMethod(j6c, "i", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return true;
            }
        });

        // 3) j6c.k() → true（was_vip：老用户回流 UI）
        XposedHelpers.findAndHookMethod(j6c, "k", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return true;
            }
        });

        // 4) 实体层 getter 兜底
        final Class<?> vipInfo = XposedHelpers.findClass(
                "com.talk.common.entity.response.VipInfo", cl);
        hookBoolGetter(vipInfo, "getIsVip");
        hookBoolGetter(vipInfo, "getWasVip");
        hookBoolGetter(vipInfo, "getHasTrailed");

        final Class<?> vipDetailResp = XposedHelpers.findClass(
                "com.talk.common.entity.response.VipDetailResp", cl);
        hookBoolGetter(vipDetailResp, "getIsVip");
        hookBoolGetter(vipDetailResp, "getWasVip");

        // VipDetailResp.VipDetail.getHas_trialed() → false（未试用 → 免费试用可点）
        try {
            Class<?> detail = XposedHelpers.findClass(
                    "com.talk.common.entity.response.VipDetailResp$VipDetail", cl);
            XposedHelpers.findAndHookMethod(detail, "getHas_trialed", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return false;
                }
            });
            // VipDetail.getExpire_time() → 远期时间戳 (2099-12-31)
            XposedHelpers.findAndHookMethod(detail, "getExpire_time", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return 4102444800L; // 2100-01-01 UTC 秒级；如毫秒由调用方换算同样够大
                }
            });
        } catch (Throwable t) {
            log("VipDetail hook skipped: " + t.getMessage());
        }

        // 5) 数据源回写：VipInfo 实例内部字段直接置 VIP 态（影响缓存与持久化快照）
        XposedHelpers.findAndHookMethod(vipInfo, "getIsVip", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                patchVipInfoFields(param.thisObject);
                return Boolean.TRUE;
            }
        });
    }

    private static void hookBoolGetter(Class<?> clazz, String method) {
        try {
            XposedHelpers.findAndHookMethod(clazz, method, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return Boolean.TRUE;
                }
            });
        } catch (Throwable t) {
            log("hook " + clazz.getSimpleName() + "." + method + " skipped: " + t.getMessage());
        }
    }

    private static void patchVipInfoFields(Object vipInfo) {
        try {
            Class<?> c = vipInfo.getClass();
            setBooleanField(c, vipInfo, "isVip", true);
            setBooleanField(c, vipInfo, "wasVip", true);
            setBooleanField(c, vipInfo, "hasTrailed", true);
            setBooleanField(c, vipInfo, "isSubscribed", true);
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
