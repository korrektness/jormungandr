import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType

class FormverSourceLayoutPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            val sourceSets = project.extensions.getByType<SourceSetContainer>()
            sourceSets.named("main") {
                java.srcDirs("src")
                resources.srcDir("resources")
            }
            sourceSets.named("test") {
                java.setSrcDirs(listOf("test"))
                resources.setSrcDirs(emptyList<String>())
            }
        }
    }
}
