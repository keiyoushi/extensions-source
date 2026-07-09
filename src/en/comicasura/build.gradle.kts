plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Asura"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://comicasura.net"
    }
}
