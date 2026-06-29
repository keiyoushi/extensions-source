plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fairy Scans"
    versionCode = 33
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://fairyscans.com"
    }
}
