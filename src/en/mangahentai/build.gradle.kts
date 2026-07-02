plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Hentai"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://mangahentai.me"
    }
}
