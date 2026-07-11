plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "JComic"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl = "https://jcomic.net"
    }
}
