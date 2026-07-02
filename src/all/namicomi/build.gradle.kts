plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NamiComi"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

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
        host("namicomi.com")
        path("/.*/title/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
