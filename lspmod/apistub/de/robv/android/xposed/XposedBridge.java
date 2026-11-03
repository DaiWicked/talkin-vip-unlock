package de.robv.android.xposed;

public final class XposedBridge {
    public static void log(String text) {}
    public static void log(Throwable t) {}
    public static String stackTraceToString(Throwable t) { return ""; }
    public static void hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook hook) {}
}
