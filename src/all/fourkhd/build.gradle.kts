plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "4KHD"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://www.4khd.com"
    }

    deeplink {
        host("zgmz.uuss.uk")
        host("4khd.com")
        path("/content/..*")
    }
}
