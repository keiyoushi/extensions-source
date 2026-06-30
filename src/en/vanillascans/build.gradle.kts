plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Vanilla Scans"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "iken"

    source {
        baseUrl = "https://vanillascans.org"
        lang = "en"
    }
}
