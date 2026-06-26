plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Kingdom Manga Online"
    className = "ReadKingdomMangaOnline"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"
    baseUrl = "https://ww5.readkingdom.com"
}
