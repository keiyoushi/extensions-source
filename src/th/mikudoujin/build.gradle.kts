plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MikuDoujin"
    versionCode = 7
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://miku-doujin.com"
    }
}
