plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHub"
    versionCode = 10
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://mangahub.io"
    }
}
