plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangastep"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://mangastep.com"
    }
}
