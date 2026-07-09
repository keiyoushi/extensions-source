plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyComic"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl = "https://mycomic.com"
    }
}
