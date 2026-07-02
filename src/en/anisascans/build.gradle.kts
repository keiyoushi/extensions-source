plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Anisa Scans"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://anisascans.in"
    }
}
