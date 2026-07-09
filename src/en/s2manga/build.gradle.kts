plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "S2Manga"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://s2read.com"
    }
}
