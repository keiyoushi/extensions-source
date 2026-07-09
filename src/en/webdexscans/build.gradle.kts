plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webdex Scans"
    versionCode = 52
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://webdexscans.com"
        versionId = 2
    }
}
