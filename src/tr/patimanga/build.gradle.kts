plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pati Manga"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://www.patimanga.com"
    }
}
