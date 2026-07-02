plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hmangakyomi"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://hmangakyomi.online"
    }
}
