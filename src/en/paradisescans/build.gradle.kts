plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Paradise Scans"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://paradisescans.com"
    }
}
