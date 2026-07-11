plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaHub"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "manhwaz"

    source {
        lang = "en"
        baseUrl = "https://manhwahub.net"
    }
}
