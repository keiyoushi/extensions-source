plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yuri-Online"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://yuri-online.com"
    }
}
