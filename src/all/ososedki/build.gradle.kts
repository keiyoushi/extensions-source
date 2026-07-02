plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OSOSEDKI"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://ososedki.com"
    }

    deeplink {
        host("ososedki.com")
        path("/photos/..*")
        path("/model/..*")
        path("/cosplay/..*")
        path("/fandom/..*")
    }
}
