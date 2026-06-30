plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rage Scans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://ragescans.com"
    }
}
