plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Wurmz"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
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
