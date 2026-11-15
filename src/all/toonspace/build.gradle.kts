import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToonSpace"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("en", "fr", "es", "de", "it", "tr", "pt", "ja", "zh", "ko", "ar", "ru", "uk").forEach {
        source {
            lang = it
            baseUrl = "https://toon.space"
        }
    }

    deeplink {
        host("toon.space")
        host("www.toon.space")
        path("/t/..*")
        path("/p/..*")
        path("/r/..*")
        path("/v/..*")
        path("/m/..*")
    }
}
