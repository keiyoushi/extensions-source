plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangas-Origines.fr"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://mangas-origines.fr"
    }
}
