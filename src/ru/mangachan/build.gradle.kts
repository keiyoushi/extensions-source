plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaChan"
    className = "MangaChan"
    versionCode = 16
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "multichan"
    baseUrl = "https://im.manga-chan.me"
}
