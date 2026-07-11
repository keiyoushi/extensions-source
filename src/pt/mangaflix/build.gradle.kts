plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Flix"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "MangaFlix"
        lang = "pt-BR"
        baseUrl = "https://mangaflix.net"
    }
}
