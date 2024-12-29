import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

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
