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

tasks.test {
    useJUnitPlatform()
}
