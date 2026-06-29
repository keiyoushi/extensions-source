plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHoNa"
    className = "MangaHoNa"
    versionCode = 51
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    baseUrl = "https://mangahona.pl"

    deeplink {
        host("mangahona.pl")
        path("/manga/.*")
        path("/czytaj/.*/.*")
    }
}
