import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NamiComi"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf(
        "en", "ar", "bg", "ca", "zh-Hans", "zh-Hant", "cs", "da",
        "fil", "fr", "de", "he", "hi", "hu",
        "id", "it", "ja", "ko", "ms",
        "pl", "pt-BR", "pt", "ru", "es-419", "es",
        "th", "tr", "uk",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://namicomi.com"
        }
    }

    deeplink {
        path("/.*/title/..*")
    }
}
