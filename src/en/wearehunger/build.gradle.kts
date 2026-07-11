plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KokoMangas"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://kokomangas.com"
        versionId = 2
    }
}
