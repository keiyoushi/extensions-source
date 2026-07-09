plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "H-Comic"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl = "https://h-comic.com"
    }
}
