plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Chainsaw Man Manga Online"
    className = "ReadChainsawManMangaOnline"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangacatalog"
    baseUrl = "https://ww5.readchainsawman.com"
}
