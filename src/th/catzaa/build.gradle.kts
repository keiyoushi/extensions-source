plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Catzaa"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "th"
        baseUrl = "https://catzaa.net"
    }
}
