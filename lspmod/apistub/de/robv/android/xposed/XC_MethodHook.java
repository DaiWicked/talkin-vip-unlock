package de.robv.android.xposed;

public abstract class XC_MethodHook {
    public XC_MethodHook() {}
    public XC_MethodHook(int priority) {}
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class Unhook {
        public void unhook() {}
    }

    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object result;
        public boolean returnEarly;
        public Throwable throwable;
        public Object getResult() { return result; }
        public void setResult(Object r) { result = r; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable t) { throwable = t; }
    }

    public interface MethodHook {}
}
