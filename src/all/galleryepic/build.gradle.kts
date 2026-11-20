import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gallery Epic"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("en", "zh").forEach {
        source {
            lang = it
            baseUrl = "https://galleryepic.com"
        }
    }

    deeplink {
        path("/en/cosplay/..*")
        path("/zh/cosplay/..*")
    }
}

dependencies {
    implementation(project(":lib:imagedecoder"))
}
