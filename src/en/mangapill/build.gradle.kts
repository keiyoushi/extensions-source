plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaPill"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangapill.com"
    }
}
