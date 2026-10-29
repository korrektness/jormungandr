plugins {
    kotlin("jvm")
    id("formver.source-layout")
}

dependencies {
    compileOnly(kotlin("compiler"))
    compileOnly(libs.kotlinx.collections.immutable)
    implementation(project(":formver.compiler-plugin:locality"))
    implementation(project(":formver.common"))

    testImplementation(kotlin("test"))
    testImplementation(libs.jqwik)
    testImplementation(kotlin("compiler"))
    testImplementation(libs.kotlinx.collections.immutable)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.register<Test>("unitTest") {
    description = "Runs this module's unit tests"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
}

// Both entry points execute the complete unit tier; check retains its test dependency.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
