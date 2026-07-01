plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toptoon.net"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "TOPTOON頂通"
        lang = "zh"
        baseUrl = "https://www.toptoon.net"
    }
}
