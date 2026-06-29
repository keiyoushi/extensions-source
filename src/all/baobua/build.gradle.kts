plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BaoBua"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://baobua.net"
    }

    deeplink {
        host("baobua.net")
        path("/category/..*")
        path("/spot/..*")
    }
}
