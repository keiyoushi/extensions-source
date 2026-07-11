plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHub"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "ar"
        baseUrl = "https://www.mangaxhentai.com"
    }
}
