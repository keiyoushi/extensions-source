plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaZone"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://manhwazone.com"
    }

    deeplink {
        host("manhwazone.com")
        host("www.manhwazone.com")
        path("/series/..*")
    }
}
