plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangafreak"
    versionCode = 14
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://ww2.mangafreak.me"
    }
}
