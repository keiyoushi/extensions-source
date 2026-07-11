plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Niceoppai"
    versionCode = 29
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://www.niceoppai.net"
    }
}
