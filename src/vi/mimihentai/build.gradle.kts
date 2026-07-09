plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MiMiHentai"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://mimihentai.net"
    }
}
