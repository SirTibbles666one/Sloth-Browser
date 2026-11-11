package com.sloth.browser;

/**
 * The one call the shell makes on a {@code GeckoResult}.
 *
 * <p>The method is {@code then(OnValueListener)}. {@code accept} takes a {@code Consumer}, and
 * there is no {@code accept(OnValueListener)} in this build: looking that up throws, the caller
 * reports "unavailable", and a finder that answered is thrown away. {@code onValue} is given the
 * value as its only argument.</p>
 */
final class SlothResult {

    interface Value {
        void onValue(Object value);
    }

    private SlothResult() {
    }

    static void listen(Object result, final Value value) throws Exception {
        final Class<?> listener = Class.forName(
                "org.mozilla.geckoview.GeckoResult$OnValueListener");
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                listener.getClassLoader(),
                new Class<?>[]{listener},
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object p, java.lang.reflect.Method method, Object[] args) {
                        String name = method.getName();
                        if ("onValue".equals(name) && args != null && args.length >= 1) {
                            value.onValue(args[args.length - 1]);
                            return null;
                        }
                        // a proxy is asked these by the result while it stores the listener;
                        // returning null for hashCode unboxes to a crash
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(p);
                        }
                        if ("equals".equals(name)) {
                            return args != null && args.length == 1 && p == args[0];
                        }
                        if ("toString".equals(name)) {
                            return "SlothResult";
                        }
                        return null;
                    }
                });
        result.getClass().getMethod("then", listener).invoke(result, proxy);
    }
}
