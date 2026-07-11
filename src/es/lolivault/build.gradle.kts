plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lolivault"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "foolslide"

    source {
        lang = "es"
        baseUrl = "https://lector.lolivault.net"
    }
}
