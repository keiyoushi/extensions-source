plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kanzenin"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://kanzenin.info"
    }
}
