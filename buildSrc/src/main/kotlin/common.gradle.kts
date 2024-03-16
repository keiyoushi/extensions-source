plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlinx-serialization")
    id("org.jmailen.kotlinter")
}

assert(!ext.has("pkgNameSuffix"))
assert(!ext.has("libVersion"))
assert(extName.chars().max().asInt < 0x180) { "Extension name should be romanized" }
assert(extClass.startsWith("."))

val theme = themePkg?.let { project(":lib-multisrc:$it") }

android {
    namespace = "eu.kanade.tachiyomi.extension"
    compileSdk = AndroidConfig.compileSdk

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            assets.setSrcDirs(listOf("assets"))
        }
    }

    defaultConfig {
        applicationIdSuffix = project.parent!!.name + "." + project.name
        minSdk = AndroidConfig.minSdk
        targetSdk = AndroidConfig.targetSdk
        versionCode = if (theme == null) {
            extVersionCode
        } else {
            theme.baseVersionCode + overrideVersionCode
        }
        versionName = "1.4.$versionCode"
        base {
            archivesName = "tachiyomi-$applicationIdSuffix-v$versionName"
        }
        manifestPlaceholders.apply {
            put("appName", "Tachiyomi: $extName")
            put("extClass", extClass)
            put("nsfw", if (isNsfw) 1 else 0)
        }
        if (theme != null && !baseUrl.isNullOrEmpty()) {
            val split = baseUrl!!.split("://")
            assert(split.size == 2)
            val path = split[1].split("/")
            manifestPlaceholders.apply {
                put("SOURCEHOST", path[0])
                put("SOURCESCHEME", split[0])
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("signingkey.jks")
            storePassword = System.getenv("KEY_STORE_PASSWORD")
            keyAlias = System.getenv("ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    dependenciesInfo {
        includeInApk = false
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
}

kotlinter {
    experimentalRules = true
    disabledRules = arrayOf(
        "experimental:argument-list-wrapping", // Doesn't play well with Android Studio
        "experimental:comment-wrapping",
    )
}

dependencies {
    if (theme != null) implementation(theme)
    implementation(project(":core"))
    compileOnly(versionCatalogs.named("libs").findBundle("common").get())
}

tasks.register("writeManifestFile") {
    doLast {
        val manifest = android.sourceSets.getByName("main").manifest
        if (!manifest.srcFile.exists()) {
            val tempFile = layout.buildDirectory.get().file("tempAndroidManifest.xml").asFile
            if (!tempFile.exists()) {
                tempFile.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest />\n")
            }
            manifest.srcFile(tempFile.path)
        }
    }
}

tasks {
    preBuild {
        dependsOn(tasks.getByName("writeManifestFile"), lintKotlin)
    }

    if (System.getenv("CI") != "true") {
        lintKotlin {
            dependsOn(formatKotlin)
        }
    }
}
