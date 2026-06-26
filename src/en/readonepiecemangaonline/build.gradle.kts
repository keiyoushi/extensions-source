plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read One Piece Manga Online"
    className = "ReadOnePieceMangaOnline"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"
    baseUrl = "https://ww12.readonepiece.com"
}
