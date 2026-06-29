plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaCloud"
    className = "MangaCloud"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("mangacloud.org")
        path("/comic/..*")
    }
}
