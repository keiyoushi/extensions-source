plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Maid - Manga"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "zmanga"

    source {
        lang = "id"
        baseUrl = "https://www.maid.my.id"
    }
}
