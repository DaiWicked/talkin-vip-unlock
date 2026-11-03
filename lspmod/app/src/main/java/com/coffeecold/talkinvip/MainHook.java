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
    private static final String TARGET_PKG = "com.talk.inapp";

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

        // 延迟 hook：Talkin 多 dex 且类在启动后才加载，用 class nativity 时机逐个挂
        hookWhenReady(cl, "defpackage.j6c", new Runnable() {
            @Override
            public void run() { /* marker */ }
        });
    }

    private static void hookWhenReady(final ClassLoader cl, final String className, final Runnable done) {
        // 方案：hook ClassLoader.loadClass，目标类一加载立即安装 hooks
        try {
            XposedHelpers.findAndHookMethod("java.lang.ClassLoader", cl, "loadClass",
                    String.class, boolean.class, new XC_MethodHook() {
                        private boolean installed = false;

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (installed) {
                                return;
                            }
                            String name = (String) param.args[0];
                            if (!"defpackage.j6c".equals(name)) {
                                return;
                            }
                            installed = true;
                            try {
                                installHooks(cl);
                                log("hooks installed on " + className);
                            } catch (Throwable t) {
                                log("install failed: " + XposedBridge.stackTraceToString(t));
                            }
                        }
                    });
        } catch (Throwable t) {
            // 直接尝试（应用已在运行时注入的场景）
            try {
                installHooks(cl);
                log("hooks installed directly");
            } catch (Throwable t2) {
                log("direct install failed: " + XposedBridge.stackTraceToString(t2));
            }
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

    private static final Field F_IS_VIP;
    private static final Field F_WAS_VIP;
    private static final Field F_HAS_TRAILED;
    private static final Field F_IS_SUBSCRIBED;

    static {
        F_IS_VIP = findField("isVip");
        F_WAS_VIP = findField("wasVip");
        F_HAS_TRAILED = findField("hasTrailed");
        F_IS_SUBSCRIBED = findField("isSubscribed");
    }

    private static Field findField(String name) {
        try {
            // 字段挂在 VipInfo 上，用 Object 反射通用获取（类由运行时 classloader 提供）
            return null; // 占位，实际在 patchVipInfoFields 中懒获取
        } catch (Throwable t) {
            return null;
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
