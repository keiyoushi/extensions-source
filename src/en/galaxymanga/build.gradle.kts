plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Galaxy Manga"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://galaxymanga.io"
    }
}
