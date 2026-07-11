plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FavComic"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "喜漫漫画"
        lang = "zh"
        baseUrl {
            mirrors(
                "https://www.favcomic.com",
                "https://www.favcomic.xyz",
                "https://www.favcomic.net",
                "https://www.favcomic.cc",
            )
        }
    }
}
