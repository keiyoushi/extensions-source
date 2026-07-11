plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaLib"
    versionCode = 75
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "libgroup"

    source {
        baseUrl {
            custom("https://mangalib.me")
        }
        lang = "ru"
    }
}
