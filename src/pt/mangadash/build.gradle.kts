plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDash"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://mangadash.net"
    }
}
