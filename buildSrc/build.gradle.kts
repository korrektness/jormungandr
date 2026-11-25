import org.gradle.kotlin.dsl.`kotlin-dsl`
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins.register("sourceLayout") {
        id = "formver.source-layout"
        implementationClass = "FormverSourceLayoutPlugin"
    }
}

if (providers.environmentVariable("FORMVER_INPUTS_DIR").isPresent) {
    layout.buildDirectory.set(layout.projectDirectory.dir("build/input-capture"))
    val kotlinVersion = libs.versions.kotlin.get()
    configurations.matching { it.name == "kotlinCompilerClasspath" || it.name.startsWith("kotlinCompilerPluginClasspath") }
        .configureEach {
            resolutionStrategy.force(
                "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-sam-with-receiver-compiler-plugin-embeddable:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-assignment-compiler-plugin-embeddable:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable:$kotlinVersion",
            )
        }
    tasks.named<KotlinCompile>("compileKotlin") {
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_2_0)
            apiVersion.set(KotlinVersion.KOTLIN_1_9)
        }
    }
    extra["formverInputCompilation"] = ":buildSrc:compileKotlin"
    apply(from = "../gradle/production-kotlin-inputs.gradle")
}
