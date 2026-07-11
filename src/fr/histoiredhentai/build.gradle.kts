plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HistoireDHentai"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://hhentai.fr"
    }
}
