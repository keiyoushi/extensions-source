plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Despair Manga"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "ar"
        baseUrl = "https://despair-manga.net"
    }
}
