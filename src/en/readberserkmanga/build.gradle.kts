plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Berserk Manga"
    className = "ReadBerserkManga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"
    baseUrl = "https://readberserk.com"
}
