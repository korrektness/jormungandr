package org.jetbrains.kotlin.formver.testrunner;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The resources that live as long as the class loader holding this class, such as a pool of verifier processes.
 * The persistent runner calls {@link Bridge#close} before it drops the loader; a JVM without the runner closes them
 * in a shutdown hook, which {@link #closeAll} removes.
 */
public final class Lifetime {
    private static final List<Closeable> resources = new ArrayList<>();
    private static Thread hook;

    private Lifetime() {}

    public static synchronized void register(Closeable resource) {
        if (hook == null) {
            hook = new Thread(() -> {
                try {
                    closeResources();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, "formver-lifetime-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
        }
        resources.add(resource);
    }

    /** Closes every registered resource, latest first, and throws the first failure with the rest suppressed. */
    public static synchronized void closeAll() throws IOException {
        if (hook != null) {
            Runtime.getRuntime().removeShutdownHook(hook);
            hook = null;
        }
        closeResources();
    }

    private static synchronized void closeResources() throws IOException {
        IOException failure = null;
        List<Closeable> closing = new ArrayList<>(resources);
        resources.clear();
        for (int i = closing.size() - 1; i >= 0; i--) {
            try {
                closing.get(i).close();
            } catch (IOException | RuntimeException e) {
                IOException wrapped = e instanceof IOException io ? io : new IOException(e);
                if (failure == null) failure = wrapped;
                else failure.addSuppressed(wrapped);
            }
        }
        if (failure != null) throw failure;
    }
}
