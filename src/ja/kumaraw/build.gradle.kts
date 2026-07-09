plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kumaraw"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://kumaraw.com"
    }

    deeplink {
        host("kumaraw.com")
        path("/manga/..*")
    }
}
