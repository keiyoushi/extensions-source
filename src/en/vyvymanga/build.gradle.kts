plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VyvyManga"
    versionCode = 40
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://vymanga.net"
    }
}
