package org.jetbrains.kotlin.formver.testrunner;

import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

/**
 * Runs one request's tests through the JUnit Platform launcher and writes JUnit XML the way Gradle's Test task does:
 * one {@code TEST-<class>.xml} per test class. It is loaded in each request's own class loader, beside the test
 * classpath, so only JDK types cross into the server.
 */
public final class Bridge {
    private Bridge() {}

    /**
     * Keys read: {@code testClassesDirs} and {@code filters}, newline-separated, and {@code resultsDir}. Keys
     * written: {@code tests}, {@code failures} and {@code skipped}, as counts of executed tests.
     */
    public static Map<String, String> run(Map<String, String> request) throws IOException {
        Set<Path> roots = new LinkedHashSet<>();
        for (String dir : lines(request.get("testClassesDirs"))) {
            if (Files.isDirectory(Path.of(dir))) roots.add(Path.of(dir));
        }
        List<TestPattern> filters = new ArrayList<>();
        for (String filter : lines(request.get("filters"))) filters.add(TestPattern.of(filter));
        Path resultsDir = Path.of(request.get("resultsDir"));
        clearResults(resultsDir);

        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClasspathRoots(roots));
        if (!filters.isEmpty()) builder.filters(new GradlePatternFilter(filters));
        LauncherDiscoveryRequest discovery = builder.build();

        XmlReportListener report = new XmlReportListener();
        LauncherFactory.create().execute(discovery, report);
        report.write(resultsDir);

        Map<String, String> result = new HashMap<>();
        result.put("tests", Integer.toString(report.tests));
        result.put("failures", Integer.toString(report.failures));
        result.put("skipped", Integer.toString(report.skipped));
        return result;
    }

    private static List<String> lines(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        for (String line : value.split("\n")) {
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    /**
     * A Gradle {@code --tests} pattern: {@code *} matches any run of characters, and a pattern that starts with an
     * uppercase letter is matched against the simple class name.
     */
    private record TestPattern(String text, Pattern regex, boolean simpleClassName) {
        static TestPattern of(String text) {
            StringBuilder regex = new StringBuilder();
            for (String part : text.split("\\*", -1)) {
                if (!regex.isEmpty()) regex.append(".*");
                regex.append(Pattern.quote(part));
            }
            boolean simple = !text.isEmpty() && Character.isUpperCase(text.charAt(0));
            return new TestPattern(text, Pattern.compile(regex.toString()), simple);
        }

        boolean matches(String className, String methodName) {
            String name = simpleClassName ? className.substring(className.lastIndexOf('.') + 1) : className;
            return regex.matcher(name).matches() || regex.matcher(name + "." + methodName).matches();
        }
    }

    /** The Test task deletes its previous XML before a run, so a file left from an earlier one is never read. */
    private static void clearResults(Path resultsDir) throws IOException {
        Files.createDirectories(resultsDir);
        try (DirectoryStream<Path> stale = Files.newDirectoryStream(resultsDir, "TEST-*.xml")) {
            for (Path file : stale) Files.delete(file);
        }
    }

    /** Keeps a test method when a pattern matches its class name, or the class name, a dot and its method name. */
    private record GradlePatternFilter(List<TestPattern> patterns) implements PostDiscoveryFilter {
        @Override
        public FilterResult apply(org.junit.platform.engine.TestDescriptor descriptor) {
            Optional<TestSource> source = descriptor.getSource();
            if (source.isEmpty() || !(source.get() instanceof MethodSource method)) {
                return FilterResult.included("not a test method");
            }
            for (TestPattern pattern : patterns) {
                if (pattern.matches(method.getClassName(), method.getMethodName())) {
                    return FilterResult.included("matches " + pattern.text());
                }
            }
            return FilterResult.excluded("matches no pattern");
        }
    }

    private record Case(String name, String className, double seconds, Throwable failure, boolean skipped) {}

    private static final class XmlReportListener implements TestExecutionListener {
        private final Map<String, List<Case>> casesByClass = new LinkedHashMap<>();
        private final Map<String, Long> started = new HashMap<>();
        private final Map<String, Instant> classStarted = new HashMap<>();
        private TestPlan plan;
        int tests;
        int failures;
        int skipped;

        @Override
        public synchronized void testPlanExecutionStarted(TestPlan testPlan) {
            plan = testPlan;
        }

        @Override
        public synchronized void executionStarted(TestIdentifier id) {
            started.put(id.getUniqueId(), System.nanoTime());
            classStarted.putIfAbsent(className(id), Instant.now());
        }

        @Override
        public synchronized void executionSkipped(TestIdentifier id, String reason) {
            if (id.isTest()) {
                record(new Case(id.getLegacyReportingName(), className(id), 0, null, true));
                return;
            }
            for (TestIdentifier descendant : plan.getDescendants(id)) {
                if (descendant.isTest()) {
                    record(new Case(descendant.getLegacyReportingName(), className(descendant), 0, null, true));
                }
            }
        }

        @Override
        public synchronized void executionFinished(TestIdentifier id, TestExecutionResult result) {
            Long start = started.remove(id.getUniqueId());
            double seconds = start == null ? 0 : (System.nanoTime() - start) / 1e9;
            Throwable failure = result.getThrowable().orElse(null);
            switch (result.getStatus()) {
                case SUCCESSFUL -> {
                    if (id.isTest()) record(new Case(id.getLegacyReportingName(), className(id), seconds, null, false));
                }
                case ABORTED -> {
                    if (id.isTest()) record(new Case(id.getLegacyReportingName(), className(id), seconds, null, true));
                }
                case FAILED -> {
                    // A container that fails, such as a class whose setup threw, is reported as Gradle reports it:
                    // one failed case for the class.
                    String name = id.isTest() ? id.getLegacyReportingName() : "initializationError";
                    record(new Case(name, className(id), seconds, failure, false));
                }
            }
        }

        private void record(Case testCase) {
            casesByClass.computeIfAbsent(testCase.className, k -> new ArrayList<>()).add(testCase);
            tests++;
            if (testCase.skipped) skipped++;
            if (testCase.failure != null) failures++;
        }

        private String className(TestIdentifier id) {
            for (Optional<TestIdentifier> current = Optional.of(id); current.isPresent();
                 current = plan.getParent(current.get())) {
                TestSource source = current.get().getSource().orElse(null);
                if (source instanceof MethodSource method) return method.getClassName();
                if (source instanceof ClassSource cls) return cls.getClassName();
            }
            return id.getLegacyReportingName();
        }

        synchronized void write(Path resultsDir) throws IOException {
            String host = hostname();
            for (Map.Entry<String, List<Case>> entry : casesByClass.entrySet()) {
                String className = entry.getKey();
                List<Case> cases = entry.getValue();
                double total = 0;
                int failed = 0;
                int skippedCases = 0;
                for (Case testCase : cases) {
                    total += testCase.seconds;
                    if (testCase.failure != null) failed++;
                    if (testCase.skipped) skippedCases++;
                }
                Instant timestamp = classStarted.getOrDefault(className, Instant.now()).truncatedTo(ChronoUnit.MILLIS);
                StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                xml.append("<testsuite name=\"").append(attr(className)).append("\" tests=\"").append(cases.size())
                    .append("\" skipped=\"").append(skippedCases).append("\" failures=\"").append(failed)
                    .append("\" errors=\"0\" timestamp=\"").append(timestamp).append("\" hostname=\"")
                    .append(attr(host)).append("\" time=\"").append(seconds(total)).append("\">\n");
                xml.append("  <properties/>\n");
                for (Case testCase : cases) {
                    xml.append("  <testcase name=\"").append(attr(testCase.name)).append("\" classname=\"")
                        .append(attr(className)).append("\" time=\"").append(seconds(testCase.seconds)).append("\"");
                    if (testCase.failure != null) {
                        xml.append(">\n    <failure message=\"").append(attr(testCase.failure.toString()))
                            .append("\" type=\"").append(attr(testCase.failure.getClass().getName())).append("\">")
                            .append(text(stackTrace(testCase.failure))).append("</failure>\n  </testcase>\n");
                    } else if (testCase.skipped) {
                        xml.append(">\n    <skipped/>\n  </testcase>\n");
                    } else {
                        xml.append("/>\n");
                    }
                }
                xml.append("  <system-out><![CDATA[]]></system-out>\n");
                xml.append("  <system-err><![CDATA[]]></system-err>\n");
                xml.append("</testsuite>\n");
                Files.writeString(resultsDir.resolve("TEST-" + className + ".xml"), xml, StandardCharsets.UTF_8);
            }
        }
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String seconds(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String stackTrace(Throwable failure) {
        StringWriter out = new StringWriter();
        failure.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    private static String attr(String value) {
        return text(value).replace("\"", "&quot;").replace("\n", "&#10;").replace("\t", "&#9;").replace("\r", "&#13;");
    }

    /** Escapes markup, and replaces the characters XML 1.0 cannot hold at all. */
    private static String text(String value) {
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(c -> {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> {
                    boolean legal = c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c <= 0xD7FF) ||
                        (c >= 0xE000 && c <= 0xFFFD) || c >= 0x10000;
                    out.appendCodePoint(legal ? c : '?');
                }
            }
        });
        return out.toString();
    }
}
