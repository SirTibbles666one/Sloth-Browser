package org.mozilla.geckoview;

/**
 * The slice of {@code GeckoResult} the shell actually calls.
 *
 * <p>{@code then(OnValueListener)} delivers the value through {@code onValue}, which takes that
 * value as its only argument. {@code accept} is a {@code Consumer}, and there is no
 * {@code accept(OnValueListener)}.</p>
 */
public class GeckoResult<T> {

    public interface OnValueListener<V, U> {
        GeckoResult<U> onValue(V value) throws Throwable;
    }

    private T value;
    private boolean done;

    public static <T> GeckoResult<T> fromValue(T value) {
        GeckoResult<T> result = new GeckoResult<>();
        result.value = value;
        result.done = true;
        return result;
    }

    public <U> GeckoResult<U> then(OnValueListener<T, U> listener) {
        if (done && listener != null) {
            try {
                listener.onValue(value);
            } catch (Throwable ignored) {
                // the shell's listener reports its own failure
            }
        }
        return new GeckoResult<>();
    }
}
