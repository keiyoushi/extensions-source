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
            resources.directories.clear()
            resources.directories.add("resources")
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

dependencies {
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
