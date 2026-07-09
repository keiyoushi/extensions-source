plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AHottie"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://ahottie.top"
    }

    deeplink {
        host("ahottie.top")
        path("/albums/..*")
        path("/tags/..*")
    }
}
