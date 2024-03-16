import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.extra

// multisrc
var ExtensionAware.baseVersionCode: Int
    get() = extra.get("baseVersionCode") as Int
    set(value) = extra.set("baseVersionCode", value)

// extensions
var ExtensionAware.extName: String
    get() = extra.get("extName") as String
    set(value) = extra.set("extName", value)

var ExtensionAware.extClass: String
    get() = extra.get("extClass") as String
    set(value) = extra.set("extClass", value)

var ExtensionAware.extVersionCode: Int
    get() = extra.get("extVersionCode") as Int
    set(value) = extra.set("extVersionCode", value)

var ExtensionAware.isNsfw: Boolean
    get() = extra.properties["isNsfw"] as? Boolean ?: false
    set(value) = extra.set("isNsfw", value)

var ExtensionAware.themePkg: String?
    get() = extra.properties["themePkg"] as? String
    set(value) = extra.set("themePkg", value!!)

var ExtensionAware.baseUrl: String?
    get() = extra.properties["baseUrl"] as? String
    set(value) = extra.set("baseUrl", value!!)

var ExtensionAware.overrideVersionCode: Int
    get() = extra.get("overrideVersionCode") as Int
    set(value) = extra.set("overrideVersionCode", value)

fun DependencyHandler.implementation(dependencyNotation: Any): Dependency? =
    add("implementation", dependencyNotation)
