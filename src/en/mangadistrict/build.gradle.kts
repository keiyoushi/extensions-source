plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga District"
    versionCode = 17
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://mangadistrict.com"
    }
}
