plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Evil production"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "cs"
        baseUrl = "https://evil-manga.eu"
    }
}
