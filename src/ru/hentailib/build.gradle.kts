plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiLib"
    versionCode = 20
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://hentailib.me")
        }
        lang = "ru"
    }
}
