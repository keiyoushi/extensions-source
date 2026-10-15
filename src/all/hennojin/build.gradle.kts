import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hennojin"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("en", "ja").forEach {
        source {
            lang = it
            baseUrl = "https://hennojin.com"
        }
    }
}
