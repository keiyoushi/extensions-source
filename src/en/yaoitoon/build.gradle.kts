plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiToon"
    versionCode = 48
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://yaoitoon.net"
        versionId = 2
    }
}
