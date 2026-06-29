plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "JComic"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl = "https://jcomic.net"
    }
}
