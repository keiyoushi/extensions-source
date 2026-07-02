plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OniSaga"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("all", "en", "fr", "ja", "pt-BR", "pt", "es-419", "es").forEach {
        source {
            lang = it
            baseUrl = "https://onisaga.com"
        }
    }

    deeplink {
        host("onisaga.com")
        path("/manga/..*")
        path("/read/..*")
    }
}
