import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaSwat"
    versionCode = 63
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ar"
        versionId = 2
        baseUrl {
            custom("https://meshmanga.com")
        }
    }

    deeplink {
        path("/series/..*")
        path("/chapter/..*")
    }
}
