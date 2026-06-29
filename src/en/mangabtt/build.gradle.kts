plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaBTT"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://manhwabtt.cc"
    }
}
