plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaForFree.net"
    className = "MangaForFreeFactory"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://mangaforfree.net"
}
