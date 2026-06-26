plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kawii Manga"
    className = "KawiiManga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("kawaiimanga.org")
        path("/.*/..*")
    }
}
