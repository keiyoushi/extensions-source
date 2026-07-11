plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OSOSEDKI"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
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
