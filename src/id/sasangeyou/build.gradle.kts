plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sasangeyou"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://sasangeyou.net"
    }
}
