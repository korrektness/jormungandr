package org.jetbrains.kotlin.formver.testrunner;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

/** A run's spec file; {@link Client} documents its keys. */
final class Spec {
    /** How long an existing server may take to answer a ping or a stop when the spec sets no {@code answerSeconds}. */
    static final long DEFAULT_ANSWER_SECONDS = 30;

    final String slot;
    final String java;
    final List<String> jvmArgs;
    final List<String> requestPropertyNames;
    final List<String> sharedClasspath;
    final List<String> requestClasspath;
    final List<String> bridgeClasspath;
    final List<String> serverClasspath;
    final List<String> testClassesDirs;
    final Map<String, String> environment = new TreeMap<>();
    final Path workingDir;
    final Path resultsDir;
    final long idleSeconds;
    final Duration answerBound;

    /** Relative paths in the spec are relative to {@code workingDir}, where the server runs. */
    private Spec(Properties properties, Path file) {
        slot = required(properties, "slot", file);
        workingDir = Path.of(required(properties, "workingDir", file)).toAbsolutePath().normalize();
        java = workingDir.resolve(required(properties, "java", file)).normalize().toString();
        jvmArgs = Protocol.list(properties.getProperty("jvmArgs"));
        requestPropertyNames = Protocol.list(properties.getProperty("requestProperties"));
        sharedClasspath = paths(properties.getProperty("sharedClasspath"));
        requestClasspath = paths(properties.getProperty("requestClasspath"));
        bridgeClasspath = paths(required(properties, "bridgeClasspath", file));
        serverClasspath = paths(required(properties, "serverClasspath", file));
        testClassesDirs = paths(required(properties, "testClassesDirs", file));
        for (String line : Protocol.list(properties.getProperty("environment"))) {
            int eq = line.indexOf('=');
            if (eq <= 0) throw new Client.UsageException(file + ": environment line without NAME=: " + line);
            environment.put(line.substring(0, eq), line.substring(eq + 1));
        }
        resultsDir = workingDir.resolve(required(properties, "resultsDir", file)).normalize();
        idleSeconds = Long.parseLong(required(properties, "idleSeconds", file));
        answerBound = Duration.ofSeconds(Long.parseLong(properties.getProperty("answerSeconds", Long.toString(DEFAULT_ANSWER_SECONDS))));
        // A slot names a directory beside the other slots, so "." and ".." are not slots.
        if (!slot.matches("[A-Za-z0-9._-]+") || slot.equals(".") || slot.equals("..")) {
            throw new Client.UsageException(file + ": slot '" + slot + "' is not a plain name.");
        }
    }

    private List<String> paths(String value) {
        List<String> result = new ArrayList<>();
        for (String entry : Protocol.list(value)) result.add(workingDir.resolve(entry).normalize().toString());
        return result;
    }

    static Spec load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new Spec(properties, file);
    }

    private static String required(Properties properties, String name, Path file) {
        String value = properties.getProperty(name);
        if (value == null) throw new Client.UsageException(file + " has no '" + name + "'.");
        return value;
    }

    /** The name of a {@code -D} argument, or null for any other JVM argument. */
    private static String propertyName(String arg) {
        if (!arg.startsWith("-D")) return null;
        int eq = arg.indexOf('=');
        return eq < 0 ? arg.substring(2) : arg.substring(2, eq);
    }

    List<String> fixedJvmArgs() {
        List<String> fixed = new ArrayList<>();
        for (String arg : jvmArgs) {
            if (!requestPropertyNames.contains(propertyName(arg))) fixed.add(arg);
        }
        return fixed;
    }

    /**
     * What a server must have been started with to serve this spec, one component per line: a name, a space, and
     * a value. The key is its hash.
     */
    String keyText(String protocolVersion) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("protocol ").append(protocolVersion).append('\n');
        text.append("java ").append(java).append(' ').append(fingerprint(Path.of(java))).append('\n');
        for (String entry : serverClasspath) {
            text.append("server-classpath:").append(entry).append(' ').append(fingerprint(Path.of(entry))).append('\n');
        }
        List<String> fixed = fixedJvmArgs();
        for (int i = 0; i < fixed.size(); i++) text.append("jvm-arg:").append(i).append(' ').append(fixed.get(i)).append('\n');
        for (Map.Entry<String, String> variable : environment.entrySet()) {
            text.append("environment:").append(variable.getKey()).append(' ').append(variable.getValue()).append('\n');
        }
        text.append("working-dir ").append(workingDir).append('\n');
        text.append("idle-seconds ").append(idleSeconds).append('\n');
        for (String entry : sharedClasspath) {
            text.append("shared-classpath:").append(entry).append(' ').append(fingerprint(Path.of(entry))).append('\n');
        }
        // A fixed property can name a file the tests read, such as a jar handed to the compiler under test. The
        // server resolves a relative name against its working directory.
        for (String arg : fixedJvmArgs()) {
            int eq = arg.indexOf('=');
            if (propertyName(arg) == null || eq < 0) continue;
            for (String part : arg.substring(eq + 1).split(File.pathSeparator)) {
                if (part.isEmpty()) continue;
                Path named = workingDir.resolve(part);
                if (Files.isRegularFile(named)) {
                    text.append("property-file:").append(named).append(' ').append(fingerprint(named)).append('\n');
                }
            }
        }
        return text.toString();
    }

    Properties request(String key, List<String> filters) {
        Properties request = new Properties();
        request.setProperty("type", "run");
        request.setProperty("key", key);
        List<String> loaded = new ArrayList<>(requestClasspath);
        loaded.addAll(bridgeClasspath);
        request.setProperty("requestClasspath", Protocol.join(loaded));
        List<String> full = new ArrayList<>(sharedClasspath);
        full.addAll(requestClasspath);
        request.setProperty("fullClasspath", String.join(File.pathSeparator, full));
        request.setProperty("requestPropertyNames", Protocol.join(requestPropertyNames));
        for (Map.Entry<String, String> property : requestProperties().entrySet()) {
            request.setProperty("property." + property.getKey(), property.getValue());
        }
        request.setProperty("testClassesDirs", Protocol.join(testClassesDirs));
        request.setProperty("filters", Protocol.join(filters));
        request.setProperty("resultsDir", resultsDir.toString());
        return request;
    }

    private Map<String, String> requestProperties() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String arg : jvmArgs) {
            String name = propertyName(arg);
            if (name == null || !requestPropertyNames.contains(name)) continue;
            int eq = arg.indexOf('=');
            result.put(name, eq < 0 ? "" : arg.substring(eq + 1));
        }
        return result;
    }

    /** Identifies a file's content by size, modification time and file key; a directory by those of its files. */
    static String fingerprint(Path path) throws IOException {
        if (!Files.exists(path)) return "missing";
        if (!Files.isDirectory(path)) return fileFingerprint(path);
        StringBuilder digest = new StringBuilder();
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                digest.append(path.relativize(file)).append(' ').append(fileFingerprint(file)).append('\n');
            }
        }
        return "dir:" + Client.sha256(digest.toString());
    }

    private static String fileFingerprint(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        return attributes.size() + "/" + attributes.lastModifiedTime().toInstant() + "/" + attributes.fileKey();
    }
}
