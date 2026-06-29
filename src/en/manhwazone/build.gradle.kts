plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaZone"
    className = "ManhwaZone"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("manhwazone.com")
        host("www.manhwazone.com")
        path("/series/..*")
    }
}
