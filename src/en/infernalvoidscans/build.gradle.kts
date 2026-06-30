plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hive Scans"
    versionCode = 42
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "iken"

    source {
        baseUrl = "https://hivetoons.org"
        lang = "en"
        versionId = 2
    }
}
