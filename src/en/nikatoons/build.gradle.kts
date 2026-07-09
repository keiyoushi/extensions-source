plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nika Toons"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://nikatoons.com"
    }
}
