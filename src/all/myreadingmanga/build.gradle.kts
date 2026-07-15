import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyReadingManga"
    versionCode = 61
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("ar", "id", "zh", "en", "de", "it", "ja", "ko", "pt-BR", "ru", "es", "tr", "vi").forEach {
        source {
            lang = it
            baseUrl = "https://myreadingmanga.info"
        }
    }
}
