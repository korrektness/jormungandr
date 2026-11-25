plugins {
    kotlin("jvm")
    id("formver.source-layout")
}

val sourceCensus by sourceSets.creating {
    java.setSrcDirs(listOf("source-census"))
}

tasks.register<JavaExec>("sourceCensus") {
    group = "verification"
    description = "Enumerates executable Kotlin regions from reconciled compiler inputs"
    classpath = sourceCensus.runtimeClasspath
    mainClass.set("org.jetbrains.kotlin.formver.census.SourceCensusKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    val checkout = rootProject.projectDir.absolutePath
    val manifest = providers.gradleProperty("formverCensusInputs")
    val output = providers.gradleProperty("formverCensusOutput")
    argumentProviders.add(CommandLineArgumentProvider { listOf(checkout, manifest.get(), output.get()) })
}

tasks.named<io.gitlab.arturbosch.detekt.Detekt>("detekt") { source("source-census") }
tasks.test { useJUnitPlatform() }

dependencies {
    add(sourceCensus.implementationConfigurationName, kotlin("compiler"))
    add(sourceCensus.implementationConfigurationName, project(":formver.common"))
    testImplementation(sourceCensus.output)
    testImplementation(project(":formver.common"))
    testImplementation(kotlin("compiler"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly(kotlin("compiler"))
    implementation(project(":formver.common"))
    implementation(project(":formver.compiler-plugin:plugin"))
    implementation(project(":formver.compiler-plugin:locality"))
    implementation(project(":formver.compiler-plugin:uniqueness"))
}
