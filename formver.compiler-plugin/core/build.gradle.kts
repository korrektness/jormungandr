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
}
