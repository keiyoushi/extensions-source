plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHub.fr"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://mangahub.fr"
    }
}
