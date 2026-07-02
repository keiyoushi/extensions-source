plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FavComic"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "喜漫漫画"
        lang = "zh"
        baseUrl("https://www.favcomic.com") {
            mirrors = listOf(
                "https://www.favcomic.xyz",
                "https://www.favcomic.net",
                "https://www.favcomic.cc",
            )
        }
    }
}
