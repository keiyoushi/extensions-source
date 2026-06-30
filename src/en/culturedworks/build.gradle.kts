plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CulturedWorks"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://culturedworks.com"
    }
}
