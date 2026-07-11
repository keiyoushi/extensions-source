plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaChan"
    versionCode = 16
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "multichan"

    source {
        baseUrl {
            custom("https://im.manga-chan.me")
        }
        lang = "ru"
        id = 7L
    }
}
