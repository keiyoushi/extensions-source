import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDNA"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "yiimadara"

    listOf("en", "all").forEach {
        source {
            lang = it
            baseUrl = "https://mangadna.com"
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
