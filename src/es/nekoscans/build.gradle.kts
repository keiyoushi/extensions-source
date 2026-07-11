plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NekoScans"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://nekoproject.org"
        // Theme changed from ZeistManga to MangaThemesia
        versionId = 3
    }
}
