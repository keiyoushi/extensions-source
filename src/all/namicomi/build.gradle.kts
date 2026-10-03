import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NamiComi"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf(
        "en", "ar", "bg", "ca", "zh-Hans", "zh-Hant", "hr", "cs", "da", "nl",
        "et", "fil", "fi", "fr", "de", "el", "he", "hi", "hu", "is",
        "ga", "id", "it", "ja", "ko", "lt", "ms", "ne", "no", "pa",
        "fa", "pl", "pt-BR", "pt", "ru", "sk", "sl", "es-419", "es", "sv",
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
