plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaFire"
    versionCode = 25
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    listOf("en", "es", "es-419", "fr", "ja", "pt", "pt-BR").forEach {
        source {
            lang = it
            baseUrl = "https://mangafire.to"
        }
    }
}
