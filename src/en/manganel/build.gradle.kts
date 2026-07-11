plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaNel"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://manganel.me"
    }
}
