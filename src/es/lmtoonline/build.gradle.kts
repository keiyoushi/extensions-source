plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lmtos"
    versionCode = 54
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://lmtos.net"
    }
}
