plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToonGod"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://www.toongod.org"
    }
}
