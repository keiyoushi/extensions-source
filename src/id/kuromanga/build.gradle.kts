plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kuro Manga"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://kuromanga.me"
    }
}
