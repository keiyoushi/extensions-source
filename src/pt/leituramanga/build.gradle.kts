plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Leitura Manga"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Leitura Mangá"
        lang = "pt-BR"
        baseUrl = "https://leituramanga.net"
    }
}
