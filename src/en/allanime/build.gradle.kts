import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AllManga"
    versionCode = 29
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://mkissa.to",
                "https://isekai2nd.com",
            )
        }
        id = 4709139914729853090L
    }

    deeplink {
        host("mkissa.to")
        host("isekai2nd.com")
        host("allmanga.to")
        path("/manga/..*")
        path("/read/..*")
    }
}
