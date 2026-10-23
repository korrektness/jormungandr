import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import java.io.File
import java.util.Properties

/**
 * Writes what the persistent test runner in `formver.test-runner` needs to run a [Test] task's tests: a spec file,
 * whose keys `Client` documents, and a `client` script that runs the runner's client with this project's Java.
 */
abstract class TestRunnerSpec : DefaultTask() {
    @get:Input
    abstract val slot: Property<String>

    @get:Input
    abstract val java: Property<String>

    @get:Input
    abstract val jvmArgs: ListProperty<String>

    @get:Input
    abstract val requestProperties: ListProperty<String>

    /** The test runtime classpath, test class directories first, as the [Test] task has it. */
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    /** The libraries that stay loaded between requests, as jar names without their version. */
    @get:Input
    abstract val sharedLibraries: ListProperty<String>

    @get:Classpath
    abstract val testClassesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val runnerClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val bridgeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val environment: MapProperty<String, String>

    /** When set, the spec's environment gets `Z3_EXE`, provided as the verification tests have it. */
    @get:Input
    abstract val provideZ3: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val z3ExeEnv: Property<String>

    @get:Internal
    abstract val gradleUserHome: Property<File>

    @get:Input
    abstract val workingDir: Property<String>

    @get:Input
    abstract val resultsDir: Property<String>

    @get:Input
    abstract val idleSeconds: Property<Long>

    @get:OutputFile
    abstract val specFile: RegularFileProperty

    @get:OutputFile
    abstract val clientScript: RegularFileProperty

    @TaskAction
    fun write() {
        val testDirs = testClassesDirs.files
        val sharedNames = sharedLibraries.get().map { Regex("""${Regex.escape(it)}-\d.*\.jar""") }
        val (shared, request) = classpath.files.partition { file ->
            file !in testDirs && sharedNames.any { it.matches(file.name) }
        }
        val env = environment.get().toMutableMap()
        if (provideZ3.get()) env["Z3_EXE"] = Z3Provisioning.z3Exe(gradleUserHome.get(), z3ExeEnv.orNull).absolutePath

        val spec = Properties()
        spec["slot"] = slot.get()
        spec["java"] = java.get()
        spec["jvmArgs"] = jvmArgs.get().joinToString("\n")
        spec["requestProperties"] = requestProperties.get().joinToString("\n")
        spec["sharedClasspath"] = shared.joinToString("\n") { it.absolutePath }
        spec["requestClasspath"] = request.joinToString("\n") { it.absolutePath }
        spec["bridgeClasspath"] = bridgeClasspath.files.joinToString("\n") { it.absolutePath }
        spec["serverClasspath"] = runnerClasspath.files.joinToString("\n") { it.absolutePath }
        spec["testClassesDirs"] = testDirs.joinToString("\n") { it.absolutePath }
        spec["environment"] = env.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
        spec["workingDir"] = workingDir.get()
        spec["resultsDir"] = resultsDir.get()
        spec["idleSeconds"] = idleSeconds.get().toString()
        specFile.get().asFile.writer().use { spec.store(it, null) }

        val script = clientScript.get().asFile
        val runnerPath = runnerClasspath.files.joinToString(File.pathSeparator) { it.absolutePath }
        script.writeText(
            "#!/bin/sh\nexec ${shellQuote(java.get())} -cp ${shellQuote(runnerPath)} " +
                "org.jetbrains.kotlin.formver.testrunner.Client \"\$@\"\n"
        )
        script.setExecutable(true)
    }

    private fun shellQuote(text: String) = "'" + text.replace("'", "'\\''") + "'"
}

/**
 * The test runtime libraries the runner keeps loaded between requests: large, so loading them dominates a short run,
 * and closed under their own dependencies, so nothing they load comes from a request's class loader.
 */
val testRunnerSharedLibraries = listOf(
    "kotlin-compiler", "kotlin-reflect", "kotlinx-coroutines-core-jvm", "kotlin-stdlib", "kotlin-stdlib-jdk7",
    "kotlin-stdlib-jdk8", "kotlin-script-runtime", "kotlin-annotations-jvm", "annotations", "silicon_2.13",
)

/**
 * The environment a runner server gets, of the variables set here: those the tests and the JVM read. The rest of a
 * shell's environment, such as `PWD`, would only make servers restart.
 */
fun testRunnerEnvironment(providers: ProviderFactory): Map<String, String> =
    listOf(
        "PATH", "HOME", "LANG", "LC_ALL", "LC_CTYPE", "TMPDIR", "XDG_CACHE_HOME", "JAVA_TOOL_OPTIONS",
        "SNAKT_TEST_DUMP_DIR", "SNAKT_VERIFICATION_TIMINGS",
    ).mapNotNull { name -> providers.environmentVariable(name).orNull?.let { name to it } }.toMap()

/**
 * Registers `<test>RunnerSpec`, writing the runner spec for [test] to `build/test-runner/<test>/`. [classpath] and
 * [testClassesDirs] are the test's own, passed apart since reading them through [test] would make running it a
 * dependency. Of the test's system properties, those in [requestProperties] are set per request; the server's environment holds only
 * [environment], since a server outlives the shell that started it.
 */
fun Project.registerTestRunnerSpec(
    test: TaskProvider<Test>,
    classpath: FileCollection,
    testClassesDirs: FileCollection,
    slot: String,
    requestProperties: List<String>,
    sharedLibraries: List<String>,
    environment: Map<String, String>,
    provideZ3: Boolean,
): TaskProvider<TestRunnerSpec> {
    val runner = configurations.maybeCreate("testRunner")
    val bridge = configurations.maybeCreate("testRunnerBridge")
    if (runner.dependencies.isEmpty()) {
        dependencies.add(runner.name, dependencies.project(mapOf("path" to ":formver.test-runner")))
        dependencies.add(bridge.name, dependencies.project(mapOf("path" to ":formver.test-runner", "configuration" to "bridgeElements")))
    }
    val outputDir = layout.buildDirectory.dir("test-runner/${test.name}")
    return tasks.register<TestRunnerSpec>("${test.name}RunnerSpec") {
        description = "Writes the persistent test runner's spec for ${test.name}"
        this.slot.set(slot)
        java.set(test.flatMap { it.javaLauncher }.map { it.executablePath.asFile.absolutePath })
        jvmArgs.set(providers.provider { test.get().allJvmArgs })
        this.requestProperties.set(requestProperties)
        this.classpath.from(classpath)
        this.sharedLibraries.set(sharedLibraries)
        this.testClassesDirs.from(testClassesDirs)
        runnerClasspath.from(runner)
        bridgeClasspath.from(bridge)
        this.environment.set(environment)
        this.provideZ3.set(provideZ3)
        z3ExeEnv.set(providers.environmentVariable("Z3_EXE"))
        gradleUserHome.set(gradle.gradleUserHomeDir)
        workingDir.set(providers.provider { test.get().workingDir.absolutePath })
        resultsDir.set(providers.provider { test.get().reports.junitXml.outputLocation.get().asFile.absolutePath })
        idleSeconds.set(1800L)
        specFile.set(outputDir.map { it.file("spec.properties") })
        clientScript.set(outputDir.map { it.file("client") })
    }
}
