plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Livre.to"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://mangalivre.to"
    }
}
