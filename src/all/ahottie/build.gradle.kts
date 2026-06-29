plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AHottie"
    className = "AHottie"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("ahottie.top")
        path("/albums/..*")
        path("/tags/..*")
    }
}
