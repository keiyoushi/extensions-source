plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kumaraw"
    className = "Kumaraw"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    baseUrl = "https://kumaraw.com"

    deeplink {
        host("kumaraw.com")
        path("/manga/..*")
    }
}
