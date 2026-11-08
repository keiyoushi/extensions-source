import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Qtoon (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "es", "fr", "pt-BR").forEach { language ->
        source {
            lang = language
            baseUrl = "https://qtoon.org"
        }
    }

    deeplink {
        path("/manga/..*")
        path("/manhwa/..*")
        path("/manhua/..*")
        path("/novel/..*")
        path("/comic/..*")
        path("/one_shot/..*")
        path("/read/..*")
    }
}
