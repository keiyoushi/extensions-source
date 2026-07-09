plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Astral Scans"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://astralscans.top"
    }
}
