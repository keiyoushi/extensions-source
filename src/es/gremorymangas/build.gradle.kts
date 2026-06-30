plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gremory Mangas"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://gremoryhistorias.org"
    }
}
