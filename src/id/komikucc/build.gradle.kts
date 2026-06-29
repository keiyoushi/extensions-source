plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komiku.cc"
    className = "Komikucc"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("komiku.cc")
        path("/komik/..*")
    }
}
