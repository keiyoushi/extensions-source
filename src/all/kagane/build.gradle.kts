import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kagane"
    versionCode = 28
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    listOf("en", "ja", "ko", "zh", "es", "es-419", "fr", "de", "pt", "pt-BR", "ru", "it", "id", "vi", "th", "pl", "hi", "ar").forEach {
        source {
            lang = it
            baseUrl = "https://kagane.to"
        }
    }
}
