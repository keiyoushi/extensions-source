plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VyvyManga"
    versionCode = 40
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://vymanga.net"
    }
}
