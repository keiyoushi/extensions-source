plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MikuDoujin"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://miku-doujin.com"
    }
}
