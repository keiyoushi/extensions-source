plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga18.Club"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "manga18"

    source {
        lang = "en"
        baseUrl = "https://manga18.club"
    }
}
