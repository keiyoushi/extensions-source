plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toptoon.net"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "TOPTOON頂通"
        lang = "zh"
        baseUrl = "https://www.toptoon.net"
    }
}
