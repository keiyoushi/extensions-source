plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Omicaso"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://omicaso.org"
    }
}
