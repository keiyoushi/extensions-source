plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiHall"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://hentaihall.com"
    }
}
