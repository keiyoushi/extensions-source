plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komiku.com"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://01.komiku.asia"
    }
}
