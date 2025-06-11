import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.extra

var ExtensionAware.baseVersionCode: Int
    get() = extra.get("baseVersionCode") as Int
    set(value) = extra.set("baseVersionCode", value)

fun Project.getDependents(): Set<Project> {
    val dependentProjects = mutableSetOf<Project>()

    rootProject.allprojects.forEach { project ->
        project.configurations.forEach { configuration ->
            configuration.dependencies.forEach { dependency ->
                if (dependency is ProjectDependency && dependency.path == path) {
                    dependentProjects.add(project)
                }
            }
        }
    }

    return dependentProjects
}

fun Project.printDependentExtensions() {
    getDependents().forEach { project ->
        if (project.path.startsWith(":src:")) {
            println(project.path)
        } else if (project.path.startsWith(":lib-multisrc:")) {
            project.getDependents().forEach { println(it.path) }
        } else if (project.path.startsWith(":lib:")) {
            project.printDependentExtensions()
        }
    }
}

@Suppress("UnusedReceiverParameter")
fun Project.configureAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = AndroidConfig.COMPILE_SDK

        defaultConfig {
            if (this is ApplicationDefaultConfig) {
                targetSdk = AndroidConfig.TARGET_SDK
            }
            minSdk = AndroidConfig.MIN_SDK
        }

        buildFeatures {
            shaders = false
        }
    }
}
