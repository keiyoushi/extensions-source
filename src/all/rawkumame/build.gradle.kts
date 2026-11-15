import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rawkuma.me"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://mangamachina.com"
        }
    }

    deeplink {
        path("/manga/..*")
        path("/es/manga/..*")
    }
}
