plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MiMiHentai"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://mimihentai.net"
    }
}
