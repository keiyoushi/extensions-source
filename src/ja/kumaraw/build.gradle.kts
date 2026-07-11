plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kumaraw"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
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
