import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yaoi.me"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf(
        "en", "ja", "zh", "vi", "id", "es", "ko", "pl", "tr", "fr", "it", "ru",
        "pt", "hu", "uk", "cs", "th", "ar", "es-419", "mn", "de", "fa", "pt-BR",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://yaoi.me"
        }
    }

    deeplink {
        path("/series/..*")
        path("/chapter/..*")
    }
}
