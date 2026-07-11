plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WNACG"
    versionCode = 23
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "紳士漫畫"
        lang = "zh"
        baseUrl = "https://www.wn07.cfd"
    }
}
