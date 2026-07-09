plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "V2PH"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://www.v2ph.com"
    }
}
