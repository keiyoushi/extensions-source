plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Oglaf"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.oglaf.com"
    }
}
