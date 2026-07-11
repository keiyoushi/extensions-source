plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PopsManga"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://popsmanga.net"
    }
}
