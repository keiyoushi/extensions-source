import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OniSaga"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

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
