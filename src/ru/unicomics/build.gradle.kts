plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "UniComics"
    className = "UniComics"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("unicomics.ru")
        path("/comics/series/..*")
    }
}
