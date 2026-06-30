plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kiwiya Scans"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "fr"
        baseUrl = "https://kiwiyascans.com"
    }
}
