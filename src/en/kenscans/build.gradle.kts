plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ken Scans"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "iken"

    source {
        baseUrl = "https://kencomics.com"
        lang = "en"
    }
}
