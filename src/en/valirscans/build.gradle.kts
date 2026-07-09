plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Valir Scans"
    versionCode = 22
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://valirscans.org"
        versionId = 3
    }
}
