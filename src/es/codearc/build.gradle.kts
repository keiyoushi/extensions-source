plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Code Arc Mangas"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://mangas.codearctraducciones.com"
    }
}
