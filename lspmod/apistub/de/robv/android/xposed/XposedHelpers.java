package de.robv.android.xposed;

import java.lang.reflect.Member;

public final class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader cl) { return null; }
    public static Class<?> findClassIfExists(String className, ClassLoader cl) { return null; }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName,
            Object... parameterTypesAndCallback) { return null; }
    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader cl,
            String methodName, Object... parameterTypesAndCallback) { return null; }

    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static void setObjectField(Object obj, String fieldName, Object value) {}
    public static Object getStaticObjectField(Class<?> clazz, String fieldName) { return null; }

    public static class Unhook {}
}
