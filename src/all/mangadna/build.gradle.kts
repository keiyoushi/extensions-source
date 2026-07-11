import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDNA"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "all").forEach {
        source {
            lang = it
            baseUrl = "https://mangadna.com"
        }
    }
}
