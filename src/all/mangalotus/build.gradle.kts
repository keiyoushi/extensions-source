import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Lotus (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "en", "ja", "zh", "ko").forEach { language ->
        source {
            lang = language
            baseUrl = "https://mangalotus.com"
        }
    }
}
