plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Luminare Translations"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://luminaretranslations.com"
    }
}
