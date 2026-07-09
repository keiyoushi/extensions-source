plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kabus Manga"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://kabusmanga.com"
    }
}
