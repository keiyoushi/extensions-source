plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Area Manga"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        name = "أريا مانجا"
        lang = "ar"
        baseUrl = "https://ar.kenmanga.com"
    }
}
