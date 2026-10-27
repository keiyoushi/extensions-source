import com.android.build.api.artifact.SingleArtifact

// Built as an APK, which lib:cronet ships as an asset
plugins {
    alias(libs.plugins.android.application)
    alias(kei.plugins.android.base)
    alias(kei.plugins.spotless)
}

android {
    namespace = "keiyoushi.lib.cronetruntime"

    defaultConfig {
        applicationId = "keiyoushi.lib.cronetruntime"
    }

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.directories.clear()
            java.directories.add("src")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = null
        }
    }
}

// Provided by the app
configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
    listOf(
        "activity", "annotation", "arch.core", "collection", "core", "customview", "fragment", "lifecycle",
        "loader", "savedstate", "versionedparcelable", "viewpager",
    ).forEach { exclude(group = "androidx.$it") }
}

dependencies {
    //noinspection UseTomlInstead
    implementation("org.chromium.net:cronet:500.1.0")
    //noinspection UseTomlInstead
    implementation("com.google.android.gms:play-services-cronet:18.1.1")
    compileOnly(libs.okhttp.core)
}

val runtimeApk = configurations.create("runtimeApk") {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("keiyoushi-cronet-runtime"))
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        artifacts.add(runtimeApk.name, variant.artifacts.get(SingleArtifact.APK))
    }
}
