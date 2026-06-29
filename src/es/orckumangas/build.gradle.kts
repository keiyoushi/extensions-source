plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OrckuMangas"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Orcku Mangas"
        lang = "es"
        baseUrl = "https://orckumangas.com"
    }
}
