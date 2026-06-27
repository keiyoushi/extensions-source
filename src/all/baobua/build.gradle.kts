plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BaoBua"
    className = "BaoBua"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("baobua.net")
        path("/category/..*")
        path("/spot/..*")
    }
}
