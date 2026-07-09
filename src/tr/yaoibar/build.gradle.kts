plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yaoibar"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://yaoibar.lol"
    }
}
