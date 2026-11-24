import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DLsite"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("ja", "en", "zh-Hans", "zh-Hant", "ko", "es", "fr", "id", "th", "vi", "pt-BR").forEach { language ->
        source {
            lang = language
            baseUrl = "https://www.dlsite.com"
        }
    }

    deeplink {
        path("/..*/work/=/product_id/..*")
        path("/..*/announce/=/product_id/..*")
    }

    deeplink {
        host("play.dlsite.com")
        path("/sample/..*")
        path("/viewer/sample/..*")
    }
}
