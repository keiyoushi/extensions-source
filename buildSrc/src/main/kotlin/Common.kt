import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

fun Project.getDependents(depth: Int = 1): Set<Project> {
    if (depth <= 0) return emptySet()
    val dependentProjects = mutableSetOf<Project>()

    rootProject.allprojects.forEach { project ->
        project.configurations.forEach { configuration ->
            configuration.dependencies.forEach { dependency ->
                if (dependency is ProjectDependency && dependency.path == path) {
                    dependentProjects.add(project)
                    dependentProjects.addAll(project.getDependents(depth - 1))
                }
            }
        }
    }

    return dependentProjects
}
