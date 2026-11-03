package de.robv.android.xposed;

public abstract class XC_MethodReplacement extends XC_MethodHook {
    public XC_MethodReplacement() {}
    public XC_MethodReplacement(int priority) {}

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        param.setResult(replaceHookedMethod(param));
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static XC_MethodReplacement returnConstant(final Object result) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return result;
            }
        };
    }
}
