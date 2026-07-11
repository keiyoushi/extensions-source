plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sushiscan.fr"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "fr"
        baseUrl = "https://sushiscan.fr"
    }
}
