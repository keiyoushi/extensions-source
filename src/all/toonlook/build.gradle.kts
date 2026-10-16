import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToonLook"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "zh", "en").forEach { language ->
        source {
            baseUrl = "https://toonlook.com"
            lang = language
        }
    }

    deeplink {
        path("/manhwa/..*")
    }
}

dependencies {
    implementation(project(":lib:imagedecoder"))
}
