plugins {
    kotlin("jvm")
    `java-test-fixtures`
    id("com.github.gmazzo.buildconfig")
    idea
    id("maven-publish")
    alias(libs.plugins.shadow)
}

sourceSets {
    main {
        java.setSrcDirs(listOf<String>())
        resources.setSrcDirs(listOf<String>())
    }
    testFixtures {
        java.setSrcDirs(listOf("test-fixtures"))
    }
    test {
        java.setSrcDirs(listOf("test", "test-gen"))
        resources.setSrcDirs(listOf("testData", "test-resources"))
    }
}

idea {
    module.generatedSourceDirs.add(projectDir.resolve("test-gen"))
}

val annotationsRuntimeClasspath: Configuration by configurations.creating { isTransitive = false }

dependencies {
    implementation(project(":formver.compiler-plugin:core")) { isTransitive = false }
    implementation(project(":formver.compiler-plugin:uniqueness")) { isTransitive = false }
    implementation(project(":formver.compiler-plugin:viper")) { isTransitive = true }
    implementation(project(":formver.compiler-plugin:cli")) { isTransitive = false }
    implementation(project(":formver.compiler-plugin:plugin")) { isTransitive = false }
    implementation(project(":formver.compiler-plugin:locality")) { isTransitive = false }
    implementation(project(":formver.common")) { isTransitive = false }
    // The compiler that loads the plugin provides these classes.
    compileOnly(kotlin("compiler"))

    testFixturesApi(kotlin("test-junit5"))
    testFixturesApi(kotlin("compiler-internal-test-framework"))
    testFixturesApi(kotlin("compiler"))
    testFixturesImplementation(project(":formver.common"))
    testFixturesImplementation(project(mapOf("path" to ":formver.test-runner", "configuration" to "bridgeElements")))
    testFixturesImplementation(project(":formver.compiler-plugin:plugin"))
    testFixturesApi(project(":formver.compiler-plugin:viper"))
    testFixturesApi(libs.viper.silicon)
    testFixturesImplementation(project(":formver.compiler-plugin:core"))
    testFixturesImplementation(project(":formver.compiler-plugin:locality"))
    testFixturesImplementation(project(":formver.compiler-plugin:uniqueness"))

    annotationsRuntimeClasspath(project(":formver.annotations"))

    testImplementation(project(":formver.compiler-plugin:plugin"))
    testImplementation(project(":formver.compiler-plugin:locality"))
    testImplementation(project(":formver.common"))
    testImplementation(project(":formver.compiler-plugin:uniqueness"))
    testRuntimeOnly(project(":formver.compiler-plugin:core"))
    testRuntimeOnly(project(":formver.compiler-plugin:viper"))

    // Dependencies required to run the internal test framework.
    testRuntimeOnly(libs.junit4)
    testRuntimeOnly(kotlin("reflect"))
    testRuntimeOnly(kotlin("test"))
    testRuntimeOnly(kotlin("script-runtime"))
    testRuntimeOnly(kotlin("annotations-jvm"))
}

buildConfig {
    useKotlinOutput {
        internalVisibility = true
    }

    packageName(group.toString())
}

// Gradle properties passed on to the tests as system properties of the same name. They select what a run does, so
// the persistent test runner sets them per request, as it does the test mode.
val forwardedProperties = listOf(
    "kotlin.test.update.test.data", "formver.recordOutcomes", "formver.vacuityReport",
    "formver.verificationCacheDirectory", "formver.verificationCacheReport",
    "formver.verificationCacheRefresh", "formver.verificationColdSample", "formver.verificationJobs",
)

fun Test.configureFormverTest() {
    dependsOn(annotationsRuntimeClasspath)

    useJUnitPlatform()
    workingDir = rootDir

    systemProperty("annotationsRuntime.classpath", annotationsRuntimeClasspath.asPath)

    // Properties required to run the internal test framework.
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")

    systemProperty("idea.ignore.disabled.plugins", "true")
    systemProperty("formver.testRun", "true")
    forwardedProperties.forEach { property ->
        project.findProperty(property)?.let { systemProperty(property, it) }
    }
    systemProperty("idea.home.path", rootDir)

    jvmArgs = listOf("-Xss30M", "-Xmx3g", "-XX:MaxMetaspaceSize=512m")
}

// Silicon reads Z3_EXE as the path of the Z3 binary.
fun Test.provideZ3() {
    val gradleUserHome = gradle.gradleUserHomeDir
    val z3ExeEnv = providers.environmentVariable("Z3_EXE").orNull
    doFirst {
        (this as Test).environment("Z3_EXE", Z3Provisioning.z3Exe(gradleUserHome, z3ExeEnv).absolutePath)
    }
}

// Each declaration worker has two method provers and a main prover. The shared declaration executor bounds jobs
// across parallel tests. SILICON_PARALLEL_VERIFIERS overrides the per-job method worker count.
val siliconParallelVerifiers = providers.environmentVariable("SILICON_PARALLEL_VERIFIERS").orNull ?: "2"

fun Test.limitSiliconParallelism() {
    environment("SILICON_PARALLEL_VERIFIERS", siliconParallelVerifiers)
}

// ./gradlew test — normal mode (full verification)
tasks.test {
    configureFormverTest()
    provideZ3()
    limitSiliconParallelism()
    systemProperty("formver.testMode", "FULL")
}

tasks.register<Test>("untilConversion") {
    description = "Runs until conversion"
    group = "verification"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    configureFormverTest()
    systemProperty("formver.testMode", "CHECK_CONVERSION")
}

// Conversion mode compares the re-rendered expected markers, which full mode does not.
tasks.check { dependsOn("untilConversion") }

tasks.register<Test>("update") {
    description = "Runs conversion and verification iff conversion changed"
    group = "verification"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    configureFormverTest()
    provideZ3()
    limitSiliconParallelism()
    systemProperty("formver.testMode", "UPDATE")
}

// One runner JVM serves all three tasks: they differ only in the test mode, which is set per request. Each spec
// provides Z3, so a conversion run and a verification run can share the JVM.
listOf(tasks.test, tasks.named<Test>("untilConversion"), tasks.named<Test>("update")).forEach { test ->
    registerTestRunnerSpec(
        test,
        sourceSets.test.get().runtimeClasspath,
        sourceSets.test.get().output.classesDirs,
        slot = "compiler",
        requestProperties = listOf("formver.testMode") + forwardedProperties,
        sharedLibraries = testRunnerSharedLibraries,
        environment = testRunnerEnvironment(providers) + ("SILICON_PARALLEL_VERIFIERS" to siliconParallelVerifiers),
        provideZ3 = true,
    )
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

val generateTests by tasks.registering(JavaExec::class) {
    inputs.dir(layout.projectDirectory.dir("testData"))
        .withPropertyName("testData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(layout.projectDirectory.dir("test-gen"))
        .withPropertyName("generatedTests")

    classpath = sourceSets.testFixtures.get().runtimeClasspath
    mainClass.set("org.jetbrains.kotlin.formver.plugin.GenerateTestsKt")
    workingDir = rootDir
}

tasks.compileTestKotlin {
    dependsOn(generateTests)
}

fun Test.setLibraryProperty(propName: String, jarName: String) {
    val path = project.configurations
        .testRuntimeClasspath.get()
        .files
        .find { """$jarName-\d.*jar""".toRegex().matches(it.name) }
        ?.absolutePath
        ?: return
    systemProperty(propName, path)
}

tasks.shadowJar {
    archiveClassifier.set("")
    // The embeddable compiler that Gradle runs relocates these packages under this prefix and hands the plugin
    // instances of its relocated classes.
    relocate("kotlinx.collections.immutable", "org.jetbrains.kotlin.kotlinx.collections.immutable")
    relocate("com.intellij", "org.jetbrains.kotlin.com.intellij")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadowJar)
        }
    }
}
