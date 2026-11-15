import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OceComic"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://ocecomic.com"
    }

    deeplink {
        host("ocecomic.com")
        host("www.ocecomic.com")
        path("/comic/..*")
        path("/search/..*")
        path("/series/..*")
        path("/writer/..*")
        path("/publisher/..*")
    }
}
