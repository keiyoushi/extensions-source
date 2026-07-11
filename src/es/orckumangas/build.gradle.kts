plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OrckuMangas"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "Orcku Mangas"
        lang = "es"
        baseUrl = "https://orckumangas.com"
    }
}
