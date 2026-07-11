plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Raw1001"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "liliana"

    source {
        lang = "ja"
        baseUrl = "https://raw1001.net"
    }
}
