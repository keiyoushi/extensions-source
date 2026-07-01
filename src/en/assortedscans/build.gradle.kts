plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Assorted Scans"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangadventure"

    source {
        lang = "en"
        baseUrl = "https://assortedscans.com"
    }
}
