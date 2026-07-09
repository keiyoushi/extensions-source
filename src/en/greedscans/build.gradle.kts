plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Greed Scans"
    versionCode = 32
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://gojoscans.com"
    }
}
