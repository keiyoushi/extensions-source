plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToomTam-Manga"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://toomtam-manga.com"
    }
}
