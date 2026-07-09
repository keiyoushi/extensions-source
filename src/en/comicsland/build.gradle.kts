plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comics Land"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://comicsland.org"
    }
}
