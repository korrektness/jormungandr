package org.jetbrains.kotlin.formver.testrunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

/**
 * Runs tests in a persistent server JVM, starting or replacing it as needed.
 *
 * <pre>
 * Client run --spec FILE [--tests PATTERN]... [--fresh-jvm] [--state-root DIR]
 * Client stop --working-dir DIR [--state-root DIR]
 * </pre>
 *
 * The spec is a properties file; list values are newline-separated. {@code slot} names the server among those of
 * one working directory. {@code java}, {@code jvmArgs}, {@code environment} ({@code NAME=VALUE} lines) and
 * {@code workingDir} start the server, with {@code serverClasspath} holding this class's own jar. Of the
 * {@code -D} JVM arguments, those named in {@code requestProperties} are set for each request instead. The
 * {@code sharedClasspath} stays loaded for the server's life; the {@code requestClasspath}, followed by
 * {@code bridgeClasspath}, is loaded anew for each request. {@code testClassesDirs} are scanned for tests, and the
 * JUnit XML goes to {@code resultsDir}. {@code idleSeconds} is how long the server waits for a request before it
 * exits. {@code answerSeconds} bounds how long an existing server may take to answer a ping or a stop;
 * one that does not answer in time is replaced.
 *
 * <p>A server is reused only when its key matches: the Java executable, the server classpath, the JVM arguments
 * other than request properties, the environment, the working directory, and the content of the shared classpath
 * and of every file a fixed {@code -D} value names. File content is identified by size, modification time and file
 * key, as Gradle identifies unchanged files.
 *
 * <p>Exit status: 0 when every test passed, 1 when a test failed or no test matched a pattern, 2 when the server
 * could not run the request.
 */
public final class Client {
    private static final String PROTOCOL_VERSION = "1";
    /** sun_path holds 108 bytes on Linux, including the terminating NUL; macOS allows fewer, 104. */
    private static final int MAX_SOCKET_PATH_BYTES = 103;
    /** {@code stop} reads no spec, so it bounds a server's answer with the spec's default. */
    private static final Duration STOP_ANSWER_BOUND = Duration.ofSeconds(Spec.DEFAULT_ANSWER_SECONDS);

    private final PrintStream out;

    private Client(PrintStream out) {
        this.out = out;
    }

    public static void main(String[] args) {
        System.exit(execute(args, System.out));
    }

    /** Runs the command in {@code args}, printing to {@code out}, and returns its exit status. */
    public static int execute(String[] args, PrintStream out) {
        try {
            return new Client(out).command(args);
        } catch (UsageException e) {
            out.println(e.getMessage());
            return 2;
        } catch (IOException | RunnerException e) {
            out.println("test runner: " + e.getMessage());
            return 2;
        }
    }

    private int command(String[] args) throws IOException, RunnerException {
        if (args.length == 0) throw new UsageException("Usage: Client run --spec FILE ... | Client stop --working-dir DIR");
        Path spec = null;
        Path workingDir = null;
        Path stateRoot = null;
        boolean fresh = false;
        List<String> filters = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--spec" -> spec = Path.of(value(args, ++i));
                case "--tests" -> filters.add(value(args, ++i));
                case "--fresh-jvm" -> fresh = true;
                case "--working-dir" -> workingDir = Path.of(value(args, ++i));
                case "--state-root" -> stateRoot = Path.of(value(args, ++i));
                default -> throw new UsageException("Unknown argument: " + args[i]);
            }
        }
        // The server runs in the spec's working directory, so the slot paths handed to it must be absolute.
        stateRoot = (stateRoot == null ? defaultStateRoot() : stateRoot).toAbsolutePath().normalize();
        return switch (args[0]) {
            case "run" -> {
                if (spec == null) throw new UsageException("run needs --spec.");
                yield run(Spec.load(spec), filters, fresh, stateRoot);
            }
            case "stop" -> {
                if (workingDir == null) throw new UsageException("stop needs --working-dir.");
                stopAll(worktreeDir(stateRoot, workingDir));
                yield 0;
            }
            default -> throw new UsageException("Unknown command: " + args[0]);
        };
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) throw new UsageException(args[i - 1] + " needs a value.");
        return args[i];
    }

    private static Path defaultStateRoot() {
        String override = System.getenv("SNAKT_TEST_RUNNER_DIR");
        if (override != null && !override.isEmpty()) return Path.of(override);
        String runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime != null && !runtime.isEmpty()) return Path.of(runtime, "snakt-test-runner");
        return Path.of(System.getProperty("java.io.tmpdir"), "snakt-test-runner-" + System.getProperty("user.name"));
    }

    /** Each working directory, such as each worktree, has its own servers. */
    private static Path worktreeDir(Path stateRoot, Path workingDir) throws IOException {
        return stateRoot.resolve(sha256(workingDir.toRealPath().toString()).substring(0, 16));
    }

    private int run(Spec spec, List<String> filters, boolean fresh, Path stateRoot) throws IOException, RunnerException {
        Path slotDir = worktreeDir(stateRoot, spec.workingDir).resolve(spec.slot);
        int socketBytes = new Slot(slotDir).socket.toString().getBytes(StandardCharsets.UTF_8).length;
        if (socketBytes > MAX_SOCKET_PATH_BYTES) {
            throw new RunnerException("the socket path " + new Slot(slotDir).socket + " is " + socketBytes +
                " bytes, over the " + MAX_SOCKET_PATH_BYTES + "-byte limit of Unix domain sockets");
        }
        createPrivateDirectories(slotDir);
        try (FileChannel lockChannel = FileChannel.open(slotDir.resolve("lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lock(lockChannel, spec.slot)) {
            Slot slot = new Slot(slotDir);
            if (fresh) slot.stop(spec.answerBound, out);
            String keyText = spec.keyText(PROTOCOL_VERSION);
            String key = sha256(keyText);
            Properties request = spec.request(key, filters);

            Properties response = null;
            if (Files.exists(slot.socket)) {
                response = slot.sendToExisting(request, spec.answerBound, out);
                if (response != null && response.getProperty("status").equals("key-mismatch")) {
                    out.println("test runner: restarting the " + spec.slot + " JVM; changed: " +
                        String.join(", ", slot.changedKeyComponents(keyText)));
                    slot.awaitExit(response);
                    response = null;
                }
            }
            if (response == null) {
                slot.start(spec, key, keyText);
                response = slot.send(request);
            }
            return report(response, filters, spec);
        }
    }

    private FileLock lock(FileChannel channel, String slot) throws IOException {
        FileLock lock = channel.tryLock();
        if (lock != null) return lock;
        out.println("test runner: waiting for another run of the " + slot + " tests in this checkout.");
        return channel.lock();
    }

    private int report(Properties response, List<String> filters, Spec spec) throws RunnerException {
        String retiring = response.getProperty("retiring");
        if (retiring != null) out.println("test runner: the " + spec.slot + " JVM exits after this run: " + retiring + ".");
        switch (response.getProperty("status", "")) {
            case "ok" -> {}
            case "error" -> throw new RunnerException("the run failed in the server:\n" + response.getProperty("message"));
            default -> throw new RunnerException("unexpected response " + response);
        }
        int tests = Integer.parseInt(response.getProperty("tests"));
        int failures = Integer.parseInt(response.getProperty("failures"));
        if (!filters.isEmpty() && tests == 0) {
            out.println("No tests found for given includes: " + filters);
            return 1;
        }
        if (failures > 0) {
            out.println(tests + " tests completed, " + failures + " failed");
            out.println("Test results: " + spec.resultsDir);
            return 1;
        }
        return 0;
    }

    private void stopAll(Path worktree) throws IOException, RunnerException {
        if (!Files.isDirectory(worktree)) return;
        try (DirectoryStream<Path> slots = Files.newDirectoryStream(worktree)) {
            for (Path slotDir : slots) {
                if (!Files.isDirectory(slotDir)) continue;
                try (FileChannel lockChannel = FileChannel.open(slotDir.resolve("lock"),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock ignored = lock(lockChannel, slotDir.getFileName().toString())) {
                    new Slot(slotDir).stop(STOP_ANSWER_BOUND, out);
                }
            }
        }
    }

    private static void createPrivateDirectories(Path dir) throws IOException {
        Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One server's files: its socket, the pid and key of the server that owns the socket, and its log. */
    private static final class Slot {
        final Path socket;
        final Path pidFile;
        final Path keyFile;
        final Path log;
        final Path sharedClasspathFile;

        Slot(Path dir) {
            socket = dir.resolve("socket");
            pidFile = dir.resolve("pid");
            keyFile = dir.resolve("key.txt");
            log = dir.resolve("server.log");
            sharedClasspathFile = dir.resolve("shared-classpath");
        }

        /**
         * Sends to the server that owns the socket. A run request first pings the server, which answers at once when
         * it is idle, so a server that takes longer than {@code bound} is hung. Returns null, after saying what was
         * observed, when no server answers there in time, so a new one can start.
         */
        Properties sendToExisting(Properties request, Duration bound, PrintStream out)
                throws IOException, RunnerException {
            boolean run = request.getProperty("type").equals("run");
            Properties first = request;
            if (run) {
                first = new Properties();
                first.setProperty("type", "ping");
                first.setProperty("key", request.getProperty("key"));
            }
            Properties answer;
            try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
                Protocol.write(channel, first);
                channel.shutdownOutput();
                answer = Protocol.read(channel, bound);
            } catch (IOException e) {
                return abandon("no server answered at " + socket + " (" + e.getMessage() + ")", out);
            } catch (TimeoutException e) {
                return abandon("the server at " + socket + " did not answer within " + bound.toSeconds() + "s", out);
            }
            if (answer.getProperty("status") == null) {
                return abandon("the server at " + socket + " closed the connection without answering", out);
            }
            if (run && answer.getProperty("status").equals("ready")) return send(request);
            return answer;
        }

        /** Gives up on the server at the socket, terminating its process if it is confirmed to be this slot's. */
        private Properties abandon(String observed, PrintStream out) throws IOException {
            Optional<ProcessHandle> process = recordedProcess();
            out.println("test runner: " + observed + "; " + describe(process) + ". Starting a new one.");
            // A server that does not answer on its socket cannot serve again.
            process.filter(ProcessHandle::isAlive).ifPresent(handle -> {
                handle.destroyForcibly();
                handle.onExit().join();
            });
            Files.deleteIfExists(socket);
            return null;
        }

        Properties send(Properties request) throws IOException, RunnerException {
            try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
                Protocol.write(channel, request);
                channel.shutdownOutput();
                Properties response = Protocol.read(channel);
                if (response.getProperty("status") == null) {
                    Optional<ProcessHandle> process = recordedProcess();
                    process.ifPresent(handle -> handle.onExit().join());
                    throw new RunnerException("the server closed the connection without a result; " +
                        describe(process) + ". Its log ends:\n" + logTail());
                }
                return response;
            }
        }

        /** Waits for a server that has answered that it is exiting to be gone, so it frees its memory first. */
        void awaitExit(Properties response) {
            ProcessHandle.of(Long.parseLong(response.getProperty("pid"))).ifPresent(handle -> handle.onExit().join());
        }

        void stop(Duration bound, PrintStream out) throws IOException, RunnerException {
            if (Files.exists(socket)) {
                Properties request = new Properties();
                request.setProperty("type", "stop");
                Properties response = sendToExisting(request, bound, out);
                if (response != null) {
                    awaitExit(response);
                    out.println("test runner: stopped the " + socket.getParent().getFileName() + " JVM (pid " +
                        response.getProperty("pid") + ").");
                }
            }
            Files.deleteIfExists(pidFile);
            Files.deleteIfExists(keyFile);
        }

        void start(Spec spec, String key, String keyText) throws IOException, RunnerException {
            Files.deleteIfExists(socket);
            Files.write(sharedClasspathFile, spec.sharedClasspath);
            Files.deleteIfExists(log);
            List<String> command = new ArrayList<>();
            command.add(spec.java);
            command.addAll(spec.fixedJvmArgs());
            command.add("-cp");
            command.add(String.join(java.io.File.pathSeparator, spec.serverClasspath));
            command.add(Server.class.getName());
            command.add(socket.toString());
            command.add(key);
            command.add(sharedClasspathFile.toString());
            command.add(Long.toString(spec.idleSeconds));
            command.add(log.toString());
            ProcessBuilder builder = new ProcessBuilder(command)
                .directory(spec.workingDir.toFile())
                .redirectErrorStream(false)
                .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()));
            builder.environment().clear();
            builder.environment().putAll(spec.environment);
            Process process = builder.start();
            process.getOutputStream().close();
            String ready;
            try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                ready = new BufferedReader(reader).readLine();
            }
            if (ready == null || !ready.startsWith("ready ")) {
                int status;
                try {
                    status = process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RunnerException("interrupted while the server was starting");
                }
                throw new RunnerException("the server exited with status " + status + " before it was ready. " +
                    "Its log ends:\n" + logTail());
            }
            Files.writeString(pidFile, Long.toString(process.pid()));
            Files.writeString(keyFile, keyText);
        }

        List<String> changedKeyComponents(String newKeyText) throws IOException {
            Map<String, String> before = components(Files.exists(keyFile) ? Files.readString(keyFile) : "");
            Map<String, String> after = components(newKeyText);
            Set<String> names = new LinkedHashSet<>(before.keySet());
            names.addAll(after.keySet());
            List<String> changed = new ArrayList<>();
            for (String name : names) {
                if (!java.util.Objects.equals(before.get(name), after.get(name))) changed.add(name);
            }
            if (changed.isEmpty()) changed.add("no recorded component (the key file is missing or from another run)");
            return changed;
        }

        private static Map<String, String> components(String keyText) {
            Map<String, String> result = new TreeMap<>();
            for (String line : keyText.split("\n")) {
                int space = line.indexOf(' ');
                if (space > 0) result.merge(line.substring(0, space), line.substring(space + 1), (a, b) -> a + "\n" + b);
            }
            return result;
        }

        /**
         * The recorded server's process, if it is still running and is still that server: a pid can be reused by an
         * unrelated process once the server is gone, so it must also carry this slot's socket among its arguments.
         */
        private Optional<ProcessHandle> recordedProcess() throws IOException {
            if (!Files.exists(pidFile)) return Optional.empty();
            return ProcessHandle.of(Long.parseLong(Files.readString(pidFile).trim()))
                .filter(handle -> handle.info().arguments()
                    .map(arguments -> List.of(arguments).contains(socket.toString()))
                    .orElse(false));
        }

        private String describe(Optional<ProcessHandle> process) throws IOException {
            if (!Files.exists(pidFile)) return "no server pid is recorded";
            String pid = Files.readString(pidFile).trim();
            return "its server, pid " + pid + (process.isPresent() ? ", is still running" : ", has exited");
        }

        private String logTail() throws IOException {
            if (!Files.exists(log)) return "(no log at " + log + ")";
            List<String> lines = Files.readAllLines(log);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 20), lines.size()));
        }
    }

    static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }

    static final class RunnerException extends Exception {
        RunnerException(String message) {
            super(message);
        }
    }
}
