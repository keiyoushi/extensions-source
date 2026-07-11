plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OreManga"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://www.oremanga.net"
    }
}
