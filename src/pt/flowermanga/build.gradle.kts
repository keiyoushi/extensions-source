plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FlowerManga.net"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://flowermangas.net"
        id = 2421010180391442293L
    }
}
