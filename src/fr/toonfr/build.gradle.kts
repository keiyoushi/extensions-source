plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toon FR"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://toonfr.com"
    }
}
