plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TodayManga"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://todaymanga.com"
    }
}
