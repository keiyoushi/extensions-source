plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Crot"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "oceanwp"

    source {
        lang = "id"
        baseUrl = "https://hentaicrot.com"
    }
}
