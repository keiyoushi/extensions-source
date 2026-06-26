plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaBang Comics"
    className = "MangaBang"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"
    baseUrl = "https://comics.manga-bang.com"
}
