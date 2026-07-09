plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaOni"
    versionCode = 19
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://manga-oni.com"
        id = 2202687009511923782L
    }
}
