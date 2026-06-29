plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TheManga"
    versionCode = 49
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://themanga.site"
    }
}
