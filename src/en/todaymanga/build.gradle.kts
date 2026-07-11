plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TodayManga"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://todaymanga.com"
    }
}
