package org.jetbrains.kotlin.formver.testrunner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link Client} against a small JUnit project compiled into a temporary directory. Its test records, in a
 * file, the pid of the JVM it ran in, how many requests the shared layer's class has served, how many times its own
 * class ran, a request property and a version baked into its source.
 */
class ClientTest {
    private static final List<String> JUNIT = List.of(System.getProperty("testRunner.junitClasspath").split(File.pathSeparator));

    @TempDir
    Path tmp;
    Path workingDir;
    Path sharedJar;
    Path testClasses;
    Path record;
    Path stateRoot;
    String answerSeconds = "30";
    String idleSeconds = "600";

    @BeforeEach
    void setUp() throws IOException {
        workingDir = Files.createDirectories(tmp.resolve("checkout"));
        sharedJar = tmp.resolve("shared.jar");
        testClasses = tmp.resolve("test-classes");
        record = tmp.resolve("record.txt");
        stateRoot = Files.createDirectories(tmp.resolve("state"));
        writeSharedJar("A");
        writeTest("v1", "");
    }

    @AfterEach
    void stopServers() {
        for (Path dir : List.of(tmp.resolve("checkout"), tmp.resolve("other-checkout"))) {
            if (Files.isDirectory(dir)) {
                Client.execute(new String[]{"stop", "--working-dir", dir.toString(), "--state-root", stateRoot.toString()},
                    new PrintStream(new ByteArrayOutputStream()));
            }
        }
    }

    @Test
    void aSecondRequestReusesTheJvmAndSharedClassesButReloadsTestClasses() throws Exception {
        assertEquals(0, run(spec("a", "x")));
        Record first = Record.read(record);
        assertEquals(0, run(spec("a", "x")));
        Record second = Record.read(record);
        assertEquals(first.pid, second.pid);
        assertEquals(1, first.sharedCalls);
        assertEquals(2, second.sharedCalls);
        assertEquals(1, second.testRuns);
    }

    @Test
    void aRequestRunsRecompiledTestClassesInTheSameJvm() throws Exception {
        assertEquals(0, run(spec("a", "x")));
        Record first = Record.read(record);
        writeTest("v2", "");
        assertEquals(0, run(spec("a", "x")));
        Record second = Record.read(record);
        assertEquals(first.pid, second.pid);
        assertEquals("v2", second.version);
    }

    @Test
    void requestPropertiesAreSetPerRequest() throws Exception {
        assertEquals(0, run(spec("a", "x")));
        Record first = Record.read(record);
        assertEquals(0, run(spec("b", "x")));
        Record second = Record.read(record);
        assertEquals(first.pid, second.pid);
        assertEquals("a", first.mode);
        assertEquals("b", second.mode);
    }

    @Test
    void aChangedSharedClasspathStartsANewJvm() throws Exception {
        assertEquals(0, run(spec("a", "x")));
        Record first = Record.read(record);
        writeSharedJar("BB");
        assertEquals(0, run(spec("a", "x")));
        Record second = Record.read(record);
        assertNotEquals(first.pid, second.pid);
        assertEquals(1, second.sharedCalls);
    }

    @Test
    void aChangedFixedPropertyStartsANewJvm() throws Exception {
        assertEquals(0, run(spec("a", "x")));
        Record first = Record.read(record);
        assertEquals(0, run(spec("a", "y")));
        Record second = Record.read(record);
        assertNotEquals(first.pid, second.pid);
    }

    @Test
    void aChangedIdleTimeoutStartsANewJvm() throws Exception {
        assertEquals(0, run(spec("a", "x")));
        Record first = Record.read(record);
        idleSeconds = "900";
        assertEquals(0, run(spec("a", "x")));
        Record second = Record.read(record);
        assertNotEquals(first.pid, second.pid);
    }

    @Test
    void fixedArgumentsSplitDifferentlyStartANewJvm() throws Exception {
        assertEquals(0, run(spec("a", List.of("-Dfixture.fixed=x -Dfixture.other=y"))));
        Record first = Record.read(record);
        assertEquals(0, run(spec("a", List.of("-Dfixture.fixed=x", "-Dfixture.other=y"))));
        Record second = Record.read(record);
        assertNotEquals(first.pid, second.pid);
    }

    @Test
    void aChangedFileNamedRelativeToTheWorkingDirectoryStartsANewJvm() throws Exception {
        Files.writeString(workingDir.resolve("input.txt"), "1");
        assertEquals(0, run(spec("a", "input.txt")));
        Record first = Record.read(record);
        Files.writeString(workingDir.resolve("input.txt"), "22");
        assertEquals(0, run(spec("a", "input.txt")));
        Record second = Record.read(record);
        assertNotEquals(first.pid, second.pid);
    }

    @Test
    void aChangedArgumentFileStartsANewJvm() throws Exception {
        Files.writeString(workingDir.resolve("jvm.args"), "-Dfixture.fixed=x");
        assertEquals(0, run(spec("a", List.of("@jvm.args"))));
        Record first = Record.read(record);
        Files.writeString(workingDir.resolve("jvm.args"), "-Dfixture.fixed=yy");
        assertEquals(0, run(spec("a", List.of("@jvm.args"))));
        Record second = Record.read(record);
        assertNotEquals(first.pid, second.pid);
    }

    @Test
    void eachWorkingDirectoryHasItsOwnJvm() throws Exception {
        assertEquals(0, run(spec("a", "x")));
        Record first = Record.read(record);
        workingDir = Files.createDirectories(tmp.resolve("other-checkout"));
        assertEquals(0, run(spec("a", "x")));
        Record second = Record.read(record);
        assertNotEquals(first.pid, second.pid);
        assertEquals(1, second.sharedCalls);
    }

    @Test
    void aFailingTestFailsTheRunAndIsInTheXml() throws Exception {
        writeTest("v1", "@Test void fails() { assertEquals(\"expected\", \"actual\"); }");
        assertEquals(1, run(spec("a", "x")));
        Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(tmp.resolve("results/TEST-fixture.SampleTest.xml").toFile());
        assertEquals(2, xml.getElementsByTagName("testcase").getLength());
        Element failure = (Element) xml.getElementsByTagName("failure").item(0);
        assertEquals("org.opentest4j.AssertionFailedError", failure.getAttribute("type"));
        assertEquals("fails()", ((Element) failure.getParentNode()).getAttribute("name"));
    }

    @Test
    void patternsSelectTestsAndAPatternMatchingNothingFails() throws Exception {
        writeTest("v1", "@Test void fails() { assertEquals(\"expected\", \"actual\"); }");
        assertEquals(0, run(spec("a", "x"), "--tests", "*SampleTest.rec*"));
        assertEquals(0, run(spec("a", "x"), "--tests", "SampleTest.records"));
        assertEquals(0, run(spec("a", "x"), "--tests", "Sample*.rec*"));
        assertEquals(1, run(spec("a", "x"), "--tests", "*NoSuchTest*"));
    }

    @Test
    void aServerThatDiedIsReportedAndReplaced() throws Exception {
        assertEquals(0, run(spec("a", "x")));
        Record first = Record.read(record);
        ProcessHandle server = ProcessHandle.of(first.pid).orElseThrow();
        server.destroyForcibly();
        server.onExit().join();
        assertEquals(0, run(spec("a", "x")));
        Record second = Record.read(record);
        assertNotEquals(first.pid, second.pid);
    }

    /**
     * The hung server is a socket this test listens on and never answers, recorded under the test JVM's own pid, so
     * the client must also decline to terminate a process that is not this slot's server.
     */
    @Test
    void aServerThatDoesNotAnswerIsReplacedWithoutTerminatingAnUnconfirmedProcess() throws Exception {
        Path slotDir = stateRoot.resolve(Client.sha256(workingDir.toRealPath().toString()).substring(0, 16))
            .resolve("fixture");
        Files.createDirectories(slotDir);
        Files.writeString(slotDir.resolve("pid"), Long.toString(ProcessHandle.current().pid()));
        answerSeconds = "0";
        try (ServerSocketChannel hung = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            hung.bind(UnixDomainSocketAddress.of(slotDir.resolve("socket")));
            assertEquals(0, run(spec("a", "x")));
        }
        assertNotEquals(ProcessHandle.current().pid(), Record.read(record).pid);
    }

    @Test
    void aServerThatDiesDuringARequestFailsTheRun() throws Exception {
        writeTest("v1", "@Test void crashes() { Runtime.getRuntime().halt(3); }");
        assertEquals(2, run(spec("a", "x")));
        writeTest("v1", "");
        assertEquals(0, run(spec("a", "x")));
    }

    /** Stopping waits for the server to exit, so without the server ending its JVM this test does not finish. */
    @Test
    void stopEndsAServerThatATestLeftAThreadRunningIn() throws Exception {
        writeTest("v1", """
            @Test void leavesAThread() {
                new Thread(() -> {
                    try {
                        new java.util.concurrent.CountDownLatch(1).await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
            """);
        assertEquals(0, run(spec("a", "x")));
        long pid = Record.read(record).pid;
        assertEquals(0, Client.execute(
            new String[]{"stop", "--working-dir", workingDir.toString(), "--state-root", stateRoot.toString()},
            capture()));
        assertTrue(ProcessHandle.of(pid).isEmpty());
    }

    @Test
    void stopAndFreshJvmEachStartANewJvm() throws Exception {
        assertEquals(0, run(spec("a", "x")));
        Record first = Record.read(record);
        assertEquals(0, Client.execute(
            new String[]{"stop", "--working-dir", workingDir.toString(), "--state-root", stateRoot.toString()},
            capture()));
        assertEquals(0, run(spec("a", "x")));
        Record second = Record.read(record);
        assertEquals(0, run(spec("a", "x"), "--fresh-jvm"));
        Record third = Record.read(record);
        assertNotEquals(first.pid, second.pid);
        assertNotEquals(second.pid, third.pid);
    }

    private record Record(long pid, int sharedCalls, int testRuns, String mode, String version) {
        static Record read(Path file) throws IOException {
            String[] fields = Files.readString(file).split(" ");
            return new Record(Long.parseLong(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                fields[3], fields[4]);
        }
    }

    private static PrintStream capture() {
        return new PrintStream(OutputStream.nullOutputStream());
    }

    private int run(Path spec, String... extra) {
        List<String> args = new ArrayList<>(List.of("run", "--spec", spec.toString(), "--state-root", stateRoot.toString()));
        args.addAll(List.of(extra));
        return Client.execute(args.toArray(String[]::new), capture());
    }

    /** {@code fixture.mode} is a request property; {@code fixture.fixed} is not. */
    private Path spec(String mode, String fixed) throws IOException {
        return spec(mode, List.of("-Dfixture.fixed=" + fixed));
    }

    private Path spec(String mode, List<String> fixedArgs) throws IOException {
        Properties spec = new Properties();
        spec.setProperty("slot", "fixture");
        spec.setProperty("java", ProcessHandle.current().info().command().orElseThrow());
        List<String> jvmArgs = new ArrayList<>(List.of("-Xmx256m", "-Dfixture.record=" + record, "-Dfixture.mode=" + mode));
        jvmArgs.addAll(fixedArgs);
        spec.setProperty("jvmArgs", Protocol.join(jvmArgs));
        spec.setProperty("requestProperties", "fixture.record\nfixture.mode");
        // Relative to the working directory, where the server loads it.
        spec.setProperty("sharedClasspath", workingDir.relativize(sharedJar).toString());
        List<String> requestClasspath = new ArrayList<>(JUNIT);
        requestClasspath.add(testClasses.toString());
        spec.setProperty("requestClasspath", Protocol.join(requestClasspath));
        spec.setProperty("bridgeClasspath", System.getProperty("testRunner.bridgeClasspath"));
        spec.setProperty("serverClasspath",
            Protocol.join(List.of(System.getProperty("testRunner.serverClasspath").split(File.pathSeparator))));
        spec.setProperty("testClassesDirs", testClasses.toString());
        spec.setProperty("environment", "PATH=" + System.getenv("PATH"));
        spec.setProperty("workingDir", workingDir.toString());
        spec.setProperty("resultsDir", tmp.resolve("results").toString());
        spec.setProperty("idleSeconds", idleSeconds);
        spec.setProperty("answerSeconds", answerSeconds);
        Path file = tmp.resolve("spec.properties");
        try (var out = Files.newBufferedWriter(file)) {
            spec.store(out, null);
        }
        return file;
    }

    private void writeSharedJar(String value) throws IOException {
        Path classes = tmp.resolve("shared-classes");
        compile(classes, List.of(), "fixture/Shared.java", """
            package fixture;
            public class Shared {
                private static int calls;
                public static synchronized int calls() { return ++calls; }
                public static String value() { return "%s"; }
            }
            """.formatted(value));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(sharedJar))) {
            jar.putNextEntry(new ZipEntry("fixture/Shared.class"));
            jar.write(Files.readAllBytes(classes.resolve("fixture/Shared.class")));
            jar.closeEntry();
        }
    }

    private void writeTest(String version, String extraMethod) throws IOException {
        List<String> classpath = new ArrayList<>(JUNIT);
        classpath.add(sharedJar.toString());
        compile(testClasses, classpath, "fixture/SampleTest.java", """
            package fixture;
            import org.junit.jupiter.api.Test;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            public class SampleTest {
                private static int runs;
                @Test void records() throws Exception {
                    runs++;
                    Files.writeString(Path.of(System.getProperty("fixture.record")), ProcessHandle.current().pid() + " " +
                        Shared.calls() + " " + runs + " " + System.getProperty("fixture.mode") + " %s");
                }
                %s
            }
            """.formatted(version, extraMethod));
    }

    private void compile(Path outDir, List<String> classpath, String path, String source) throws IOException {
        if (Files.exists(outDir)) {
            try (Stream<Path> old = Files.walk(outDir)) {
                for (Path file : old.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        Path sources = Files.createDirectories(tmp.resolve("sources"));
        Path file = sources.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        Files.createDirectories(outDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>(List.of("-d", outDir.toString(), "-proc:none", "-nowarn"));
        if (!classpath.isEmpty()) {
            args.add("-cp");
            args.add(String.join(File.pathSeparator, classpath));
        }
        args.add(file.toString());
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = compiler.run(null, null, errors, args.toArray(String[]::new));
        assertEquals(0, status, errors.toString(StandardCharsets.UTF_8));
    }
}
