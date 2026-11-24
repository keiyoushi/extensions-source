import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NHentai.website"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "galleryadults"

    listOf("en", "ja", "zh").forEach { language ->
        source {
            lang = language
            baseUrl {
                mirrors(
                    "https://nhentai.website",
                    "https://nhentai.jp.net",
                    "https://hitomi.uk",
                    "https://9hentai.uk",
                    "https://9hentai.us",
                )
            }
        }
    }

    deeplink {
        host("nhentai.website")
        host("nhentai.jp.net")
        host("hitomi.uk")
        host("9hentai.uk")
        host("9hentai.us")
        path("/g/..*")
        path("/read/..*")
    }
}
