plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tsundoku Traduções"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "pt-BR"
        baseUrl = "https://tsundoku.com.br"
    }
}
