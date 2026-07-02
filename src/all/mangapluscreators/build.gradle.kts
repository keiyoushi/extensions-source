plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MANGA Plus Creators by SHUEISHA"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://mangaplus-creators.jp"
        }
    }

    deeplink {
        host("mangaplus-creators.jp")
        host("medibang.com")
        path("/titles/..*")
    }
}
