plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toon FR"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://toonfr.com"
    }
}
