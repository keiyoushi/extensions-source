plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lava Scans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "ar"
        baseUrl = "https://lavascans.com"
    }
}
