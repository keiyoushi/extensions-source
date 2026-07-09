plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read One Piece Manga Online"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww12.readonepiece.com"
    }
}
