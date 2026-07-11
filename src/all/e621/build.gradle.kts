plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "e621"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://e621.net"
    }
}
