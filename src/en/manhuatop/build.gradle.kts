plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhuaTop"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manhuatop.org"
    }
}
