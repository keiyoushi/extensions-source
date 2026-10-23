import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rawkuma.me"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://rawkuma.me"
        }
    }

    deeplink {
        path("/manga/..*")
        path("/es/manga/..*")
    }
}
