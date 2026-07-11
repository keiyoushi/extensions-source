plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiToon"
    versionCode = 48
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://yaoitoon.net"
        versionId = 2
    }
}
