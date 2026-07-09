plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ComicLand"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://comicland.org"
    }
}
