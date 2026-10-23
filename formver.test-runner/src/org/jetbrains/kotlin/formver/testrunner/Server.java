package org.jetbrains.kotlin.formver.testrunner;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.InvocationTargetException;
import java.net.StandardProtocolFamily;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The persistent test JVM. It keeps one class loader over the shared classpath for its whole life, and runs each
 * request in a new class loader over the request classpath, parented to the shared one. It serves one request at a
 * time on a Unix domain socket and exits when it has been idle for the given time, when it is told to stop, when a
 * request carries a different key, or after a request that left metaspace nearly full.
 *
 * <p>Usage: {@code Server <socket> <key> <shared classpath file> <idle seconds> <log file>}.
 */
public final class Server {
    /** Metaspace use, after a collection, above which the server retires rather than take another request. */
    private static final double METASPACE_RETIRE_FRACTION = 0.75;

    private final Path socket;
    private final String key;
    private final ClassLoader shared;
    private final long idleSeconds;
    private final ScheduledExecutorService idleTimer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "test-runner-idle");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> idleExit;

    private Server(Path socket, String key, ClassLoader shared, long idleSeconds) {
        this.socket = socket;
        this.key = key;
        this.shared = shared;
        this.idleSeconds = idleSeconds;
    }

    public static void main(String[] args) throws IOException {
        Path socket = Path.of(args[0]);
        String key = args[1];
        List<String> sharedEntries = Files.readAllLines(Path.of(args[2]));
        long idleSeconds = Long.parseLong(args[3]);
        Path log = Path.of(args[4]);

        // A cached jar connection keeps serving the content a jar had when first opened, so a rebuilt project jar
        // would hand a later request its old resources.
        URLConnection.setDefaultUseCaches("jar", false);
        // Unnamed, as Gradle's are: a named loader prefixes every stack frame it loaded with its name.
        ClassLoader shared = new URLClassLoader(urls(sharedEntries), ClassLoader.getPlatformClassLoader());

        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            // The client waits for this line; from here on the output goes to the log, since the client is gone.
            System.out.println("ready " + ProcessHandle.current().pid());
            System.out.flush();
            PrintStream logStream = new PrintStream(new FileOutputStream(log.toFile(), true), true);
            System.setOut(logStream);
            System.setErr(logStream);
            new Server(socket, key, shared, idleSeconds).serve(server);
        }
        // A thread a test left running would keep the JVM alive, and the client waits for it to exit.
        System.out.flush();
        Runtime.getRuntime().halt(0);
    }

    private static URL[] urls(List<String> entries) throws IOException {
        URL[] urls = new URL[entries.size()];
        for (int i = 0; i < urls.length; i++) urls[i] = Path.of(entries.get(i)).toUri().toURL();
        return urls;
    }

    private void serve(ServerSocketChannel server) throws IOException {
        while (true) {
            armIdleExit();
            try (SocketChannel connection = server.accept()) {
                disarmIdleExit();
                Properties request = Protocol.read(connection);
                String type = request.getProperty("type", "");
                Properties response = new Properties();
                boolean exit;
                if (type.equals("stop")) {
                    response.setProperty("status", "stopped");
                    exit = true;
                } else if (!key.equals(request.getProperty("key"))) {
                    response.setProperty("status", "key-mismatch");
                    exit = true;
                } else if (type.equals("ping")) {
                    response.setProperty("status", "ready");
                    exit = false;
                } else if (type.equals("run")) {
                    exit = run(request, response);
                } else {
                    response.setProperty("status", "error");
                    response.setProperty("message", "Unknown request type '" + type + "'.");
                    exit = false;
                }
                response.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
                try {
                    Protocol.write(connection, response);
                } catch (IOException e) {
                    // The client is gone, such as after an interrupt, so the log is the only place left to say so;
                    // the next client gets its own answer.
                    System.out.println("Could not answer a client: " + e);
                }
                if (exit) {
                    Files.deleteIfExists(socket);
                    return;
                }
            }
        }
    }

    private synchronized void armIdleExit() {
        idleExit = idleTimer.schedule(() -> {
            System.out.println("Idle for " + idleSeconds + "s; exiting.");
            try {
                Files.deleteIfExists(socket);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Runtime.getRuntime().halt(0);
        }, idleSeconds, TimeUnit.SECONDS);
    }

    private synchronized void disarmIdleExit() {
        idleExit.cancel(false);
    }

    /** Runs one request; returns whether the server should exit after answering it. */
    private boolean run(Properties request, Properties response) throws IOException {
        List<String> requestClasspath = Protocol.list(request.getProperty("requestClasspath"));
        Properties savedProperties = (Properties) System.getProperties().clone();
        ClassLoader savedContext = Thread.currentThread().getContextClassLoader();
        boolean exit = false;
        try (URLClassLoader loader = new URLClassLoader(urls(requestClasspath), shared)) {
            for (String name : Protocol.list(request.getProperty("requestPropertyNames"))) {
                String value = request.getProperty("property." + name);
                if (value == null) System.clearProperty(name);
                else System.setProperty(name, value);
            }
            System.setProperty("java.class.path", request.getProperty("fullClasspath", ""));
            Thread.currentThread().setContextClassLoader(loader);
            Map<String, String> bridgeRequest = new HashMap<>();
            for (String name : List.of("testClassesDirs", "filters", "resultsDir")) {
                bridgeRequest.put(name, request.getProperty(name, ""));
            }
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) loader
                .loadClass("org.jetbrains.kotlin.formver.testrunner.Bridge")
                .getMethod("run", Map.class)
                .invoke(null, bridgeRequest);
            response.putAll(result);
            response.setProperty("status", "ok");
        } catch (InvocationTargetException e) {
            fail(response, e.getCause());
            // After an Error the JVM's state cannot be trusted for another request.
            exit = e.getCause() instanceof Error;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(response, e);
        } finally {
            Thread.currentThread().setContextClassLoader(savedContext);
            System.setProperties(savedProperties);
        }
        String retire = metaspaceRetirement();
        if (retire != null) {
            response.setProperty("retiring", retire);
            exit = true;
        }
        return exit;
    }

    private static void fail(Properties response, Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        response.setProperty("status", "error");
        response.setProperty("message", trace.toString());
    }

    /** Returns why the server should retire because of metaspace, or null when there is room for another request. */
    private static String metaspaceRetirement() {
        MemoryPoolMXBean metaspace = null;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().equals("Metaspace")) metaspace = pool;
        }
        if (metaspace == null || metaspace.getUsage().getMax() <= 0) return null;
        if (fraction(metaspace.getUsage()) < METASPACE_RETIRE_FRACTION) return null;
        // Finished requests' class loaders are garbage until a collection says otherwise.
        System.gc();
        MemoryUsage usage = metaspace.getUsage();
        if (fraction(usage) < METASPACE_RETIRE_FRACTION) return null;
        return String.format(java.util.Locale.ROOT, "metaspace holds %d of %d MB after a collection",
            usage.getUsed() >> 20, usage.getMax() >> 20);
    }

    private static double fraction(MemoryUsage usage) {
        return (double) usage.getUsed() / usage.getMax();
    }
}
