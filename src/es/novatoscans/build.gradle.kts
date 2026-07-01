plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Novato Scans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "es"
        baseUrl = "https://www.novatoscans.top"
    }
}
