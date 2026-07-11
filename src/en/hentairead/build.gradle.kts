plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiRead"
    versionCode = 10
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://hentairead.com"
        versionId = 2
    }
}
