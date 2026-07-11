plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Starlight Scan"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "pt-BR"
        baseUrl = "https://starligthscan.com"
    }
}
