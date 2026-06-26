plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MANGA Plus Creators by SHUEISHA"
    className = "MangaPlusCreatorsFactory"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("mangaplus-creators.jp")
        host("medibang.com")
        path("/titles/..*")
    }
}
