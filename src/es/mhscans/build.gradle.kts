plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MHScans"
    versionCode = 14
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://mhscans.com"
    }
}
