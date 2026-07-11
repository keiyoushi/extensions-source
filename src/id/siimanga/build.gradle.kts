plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Siikomik"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "id"
        baseUrl = "https://siikomik.net"
        versionId = 3
    }
}
