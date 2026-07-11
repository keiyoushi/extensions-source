plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangabat"
    versionCode = 7
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangabox"

    source {
        lang = "en"
        baseUrl = "https://www.mangabats.com"
    }
}
