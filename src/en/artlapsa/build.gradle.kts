plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Art Lapsa"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://artlapsa.com"
    }
}
