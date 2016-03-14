package org.reactivecouchbase.microservices.lib;

import org.reactivecouchbase.common.Throwables;

public class Utils {

    public interface UnsafeSupplier<T> {
        T get() throws Exception;
    }

    public static <T> T unsafe(UnsafeSupplier<T> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
