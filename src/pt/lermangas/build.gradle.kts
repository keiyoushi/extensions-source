plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ler Mangas"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://lermangas.me"
    }
}
