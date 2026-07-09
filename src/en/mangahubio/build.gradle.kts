plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHub"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://mangahub.io"
    }
}
