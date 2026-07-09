plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Origines"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://hentai-origines.com"
    }
}
