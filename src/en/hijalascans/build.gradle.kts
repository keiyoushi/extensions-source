plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hijala Scans"
    className = "HijalaScans"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "iken"

    source {
        baseUrl = "https://en-hijala.com"
        lang = "en"
    }
}
