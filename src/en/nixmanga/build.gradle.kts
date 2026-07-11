plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NixManga"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://nixmanga.com"
    }
}
