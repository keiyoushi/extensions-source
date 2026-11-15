import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Swarm"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "es", "es-419", "fr", "id", "ko", "pl", "pt-BR", "ru", "th", "tr", "vi", "zh").forEach { langCode ->
        source {
            lang = langCode
            baseUrl = "https://swarm.ws"
        }
    }

    deeplink {
        path("/comic/..*")
    }
}
