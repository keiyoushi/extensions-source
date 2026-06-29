plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga UP!"
    className = "MangaUpFactory"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("global.manga-up.com")
        host("www.global.manga-up.com")
        path("/manga/..*")
    }
}
