plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Universo Hentai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "gattsu"

    source {
        lang = "pt-BR"
        baseUrl = "https://universohentai.com"
    }
}
