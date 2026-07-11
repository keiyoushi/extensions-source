plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiLib"
    versionCode = 20
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://hentailib.me")
        }
        lang = "ru"
    }
}
