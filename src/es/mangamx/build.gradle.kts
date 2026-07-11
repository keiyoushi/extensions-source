plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaOni"
    versionCode = 19
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://manga-oni.com"
        id = 2202687009511923782L
    }
}
