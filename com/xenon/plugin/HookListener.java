package com.xenon.plugin;


/**
 * {@code hooks.length == priorities.length} is always verified
 * @author Zenon
 */
public class HookListener<T> {

    private Object[] hooks = new Object[0];
    private int[] priorities = new int[0];
    public final T state;
    public final boolean critical;

    public HookListener(T state, boolean critical) {
        this.state = state;
        this.critical = critical;
    }

    void registerHook(Hook<T> h, Priority p) {
        final int len = hooks.length;
        final int new_len = len + 1;
        var ar1 = new Object[new_len];
        var ar2 = new int[new_len];

        int i = 0;
        for (; i < len; i++)
            if (priorities[i] < p.ordinal())
                break;

        System.arraycopy(hooks, 0, ar1, 0, i);
        System.arraycopy(priorities, 0, ar2, 0, i);
        ar1[i] = h;
        ar2[i] = p.ordinal();
        if (i != len) { // optional since len - i can never be less than 0 (i <= len always)
            System.arraycopy(hooks, i, ar1, i + 1, len - i);
            System.arraycopy(priorities, i, ar2, i + 1, len - i);
        }
        hooks = ar1;
        priorities = ar2;
    }

    void removeHook(Hook<?> h) {
        final int len = hooks.length;
        final int new_len = len - 1;
        var ar1 = new Object[new_len];
        var ar2 = new int[new_len];
        int i = 0;
        for (; i < len; i++)
            if (hooks[i] ==h)
                break;

        if (i == len)
            throw new RuntimeException("Trying to remove an unregistered hook");

        System.arraycopy(hooks, 0, ar1, 0, i);
        System.arraycopy(priorities, 0, ar2, 0, i);
        System.arraycopy(hooks, i + 1, ar1, i, len - i - 1);
        System.arraycopy(priorities, i + 1, ar2, i, len - i - 1);
        hooks = ar1;
        priorities = ar2;
    }

    @SuppressWarnings("unchecked")
    public void unroll() {
        for (var h : hooks)
            if (((Hook<T>)h).call(state))
                return;
    }

}
