plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Aarlas"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "https://www.arlas.online"
    }
}
