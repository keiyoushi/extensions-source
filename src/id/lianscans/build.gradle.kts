plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LianScans"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://www.lianscans.com"
    }
}
