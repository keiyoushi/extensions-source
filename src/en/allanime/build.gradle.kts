import keiyoushi.gradle.extension.dsl.mirrorUrls

plugins {
    alias(kei.plugins.extension)
}

extension {
    name = "AllManga"
    className = "AllManga"
    nsfw = true
    versionCode = 10
    source {
        name = "AllManga"
        lang = "en"
        id = 4709139914729853090
        baseUrl(
            mirrorUrls(
                "https://allmanga.to",
                "https://allanime.day",
            ),
        )
        deeplink {
            path("/manga/..*")
            path("/read/..*")
        }
    }
}
