plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tia Manhwa"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://tiamanhwa.com"
    }
}
