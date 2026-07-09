plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonizy"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://toonizy.com"
    }
}
