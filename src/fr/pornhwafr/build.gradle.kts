plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pornhwa.fr"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        name = "Pornwha.fr"
        lang = "fr"
        baseUrl = "https://pornhwa.fr"
    }
}
