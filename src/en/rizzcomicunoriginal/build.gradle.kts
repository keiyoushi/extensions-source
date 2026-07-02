plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rizz Comic (unoriginal)"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://rizzcomic.com"
    }
}
