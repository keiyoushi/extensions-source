plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Es.Mi2Manga"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://es.mi2manga.com"
    }
}
