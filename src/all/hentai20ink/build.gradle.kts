import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai20.ink (unoriginal)"
    theme = "madara"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "en", "ko").forEach {
        source {
            baseUrl = "https://www.hentai20.ink"
            lang = it
        }
    }
}
