plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dassou Scan"
    versionCode = 51
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://dassouscan.com"
        versionId = 2
    }
}
