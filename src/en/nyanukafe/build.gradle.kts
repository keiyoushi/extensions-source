plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nyanu Kafe"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://nyanukafe.com"
    }
}
