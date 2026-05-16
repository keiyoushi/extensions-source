plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.samWithReceiver)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spotless)
    `java-gradle-plugin`
}

// Configuration should be synced with [/gradle/build-logic/src/main/kotlin/PluginSpotless.kt]
val ktlintVersion = libs.ktlint.bom.get().version
val editorConfigFile = rootProject.file("../../.editorconfig")
spotless {
    kotlin {
        target("src/**/*.kt", "*.kts")
        ktlint(ktlintVersion)
            .setEditorConfigPath(editorConfigFile)
            .editorConfigOverride(
                mapOf(
                    "max_line_length" to 2147483647,
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly(libs.android.gradle)
    compileOnly(libs.kotlin.gradle)
    implementation(libs.ksp.gradle)
    implementation(libs.spotless.gradle)
    implementation(libs.tapmoc.gradle)
    implementation(libs.kotlin.json)

    // These allow us to reference the dependency catalog inside our compiled plugins
    compileOnly(files(libs::class.java.superclass.protectionDomain.codeSource.location))
    compileOnly(files(kei::class.java.superclass.protectionDomain.codeSource.location))
}

samWithReceiver {
    annotation("org.gradle.api.HasImplicitReceiver")
}

gradlePlugin {
    plugins {
        register("android-base") {
            id = kei.plugins.android.base.get().pluginId
            implementationClass = "PluginAndroidBase"
        }
        register("extension") {
            id = kei.plugins.extension.get().pluginId
            implementationClass = "PluginExtension"
        }
        register("extension-legacy") {
            // Legacy plugin id is not exposed via the catalog (would collide with the
            // `extension` leaf accessor). Existing `apply plugin: "kei.plugins.extension.legacy"`
            // calls in legacy build.gradle files continue to resolve via this registration.
            id = "kei.plugins.extension.legacy"
            implementationClass = "PluginExtensionLegacy"
        }
        register("library") {
            id = kei.plugins.library.get().pluginId
            implementationClass = "PluginLibrary"
        }
        register("multisrc") {
            id = kei.plugins.multisrc.get().pluginId
            implementationClass = "PluginMultiSrc"
        }
        register("spotless") {
            id = kei.plugins.spotless.get().pluginId
            implementationClass = "PluginSpotless"
        }
    }
}
