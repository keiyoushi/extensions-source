plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AllPornComic"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://allporncomic.com"
    }
}
