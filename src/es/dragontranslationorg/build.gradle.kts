plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DragonTranslation.org"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://dragontranslation.org"
    }
}
