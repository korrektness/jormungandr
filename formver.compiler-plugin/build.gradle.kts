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
    implementation(kotlin("compiler"))

    testFixturesApi(kotlin("test-junit5"))
    testFixturesApi(kotlin("compiler-internal-test-framework"))
    testFixturesApi(kotlin("compiler"))
    testFixturesImplementation(project(":formver.common"))
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
    systemProperty("idea.home.path", rootDir)

    project.findProperty("kotlin.test.update.test.data")?.let {
        systemProperty("kotlin.test.update.test.data", it)
    }
    project.findProperty("formver.recordOutcomes")?.let {
        systemProperty("formver.recordOutcomes", it)
    }

    jvmArgs = listOf("-Xss30M", "-Xmx2g", "-XX:MaxMetaspaceSize=512m")
}

// Silicon reads Z3_EXE as the path of the Z3 binary.
fun Test.provideZ3() {
    val gradleUserHome = gradle.gradleUserHomeDir
    val z3ExeEnv = providers.environmentVariable("Z3_EXE").orNull
    doFirst {
        (this as Test).environment("Z3_EXE", Z3Provisioning.z3Exe(gradleUserHome, z3ExeEnv).absolutePath)
    }
}

// Silicon defaults to one parallel verifier, each with its own Z3 process, per core, and every test starts a fresh
// Silicon. Starting those processes dominates the suite's run time; two verifiers was the fastest setting measured.
// A single verifier is slower still, as one Z3 instance then accumulates state across a test's methods.
// Setting SILICON_PARALLEL_VERIFIERS in the environment overrides this.
fun Test.limitSiliconParallelism() {
    if (providers.environmentVariable("SILICON_PARALLEL_VERIFIERS").orNull == null) {
        environment("SILICON_PARALLEL_VERIFIERS", "2")
    }
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
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadowJar)
        }
    }
}
