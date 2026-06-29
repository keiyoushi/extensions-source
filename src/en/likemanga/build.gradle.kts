plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LikeManga"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://likemanga.ink"
    }

    deeplink {
        host("likemanga.io")
        path("/..*")
    }
}
