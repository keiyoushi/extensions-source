plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Leitura Manga"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "Leitura Mangá"
        lang = "pt-BR"
        baseUrl = "https://leituramanga.net"
    }
}
