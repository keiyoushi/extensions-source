plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHub.fr"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://mangahub.fr"
    }
}
