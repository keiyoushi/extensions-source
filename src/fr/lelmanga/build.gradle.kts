plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lelmanga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "fr"
        baseUrl = "https://www.lelmanga.com"
    }
}
