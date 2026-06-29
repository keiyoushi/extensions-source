plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Muito Hentai"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://www.muitohentai.com"
    }
}
