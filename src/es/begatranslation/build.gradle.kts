plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bega Translation"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://begatranslation.com"
    }
}
