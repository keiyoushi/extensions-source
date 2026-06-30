plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Taurus Fansub"
    versionCode = 9
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://lectortaurus.com"
    }
}
