plugins {
    kotlin("jvm")
    id("formver.source-layout")
}

dependencies {
    compileOnly(project(":formver.common"))
    compileOnly(project(":formver.compiler-plugin:viper"))
    compileOnly(project(":formver.compiler-plugin:uniqueness"))
    compileOnly(project(":formver.compiler-plugin:locality"))
    compileOnly(kotlin("compiler"))

    // TODO: figure out how to avoid this dependency
    compileOnly(libs.viper.silicon)

    testImplementation(project(":formver.compiler-plugin:viper"))
    testImplementation(kotlin("compiler"))
    testImplementation(kotlin("test"))
    testImplementation(libs.jqwik)
    testRuntimeOnly(project(":formver.common"))
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
