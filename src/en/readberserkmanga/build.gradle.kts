plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Berserk Manga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://readberserk.com"
    }
}
