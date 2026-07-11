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
    implementation(libs.kotlin.json)
    implementation(libs.spotless.gradle)
    implementation(libs.tapmoc.gradle)

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
            implementationClass = "AndroidBasePlugin"
        }
        register("extension") {
            id = kei.plugins.extension.get().pluginId
            implementationClass = "ExtensionPlugin"
        }
        register("library") {
            id = kei.plugins.library.get().pluginId
            implementationClass = "LibraryPlugin"
        }
        register("spotless") {
            id = kei.plugins.spotless.get().pluginId
            implementationClass = "SpotlessPlugin"
        }
        register("theme") {
            id = kei.plugins.multisrc.get().pluginId
            implementationClass = "ThemePlugin"
        }
    }
}
