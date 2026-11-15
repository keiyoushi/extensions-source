import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Simply Hentai"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("en", "ja", "zh", "ko", "es", "ru", "fr", "de", "it", "pl").forEach {
        source {
            lang = it
            baseUrl = "https://www.simply-hentai.com"
            versionId = 2
        }
    }

    deeplink {
        host("www.simply-hentai.com")
        host("simply-hentai.com")
        host("doujin.sexy")
        host("www.doujin.sexy")
        path("/..*/..*")
    }
}
