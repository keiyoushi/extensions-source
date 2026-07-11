plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Paradise BL"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl = "https://paradise-bl.com"
    }
}
