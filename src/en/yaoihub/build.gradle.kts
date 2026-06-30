plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yaoihub"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://yaoihub.net"
    }
}
