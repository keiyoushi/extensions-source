plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaLib"
    versionCode = 75
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "libgroup"

    source {
        baseUrl("https://mangalib.me") {
            withCustom = true
        }
        lang = "ru"
    }
}
