plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaCrazy"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "all"
        baseUrl = "https://mangacrazy.net"
    }
}
