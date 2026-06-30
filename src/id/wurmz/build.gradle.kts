plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Wurmz"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://wurmz.net"
    }

    deeplink {
        host("wurmz.net")
        path("/detail/.*")
    }
}
