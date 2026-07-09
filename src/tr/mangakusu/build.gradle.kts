plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Kusu"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://mangakusu.com"
    }
}
