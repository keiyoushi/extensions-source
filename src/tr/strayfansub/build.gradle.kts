plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Stray Fansub"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://strayfansub.net"
    }
}
