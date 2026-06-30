plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FuryoSquad"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://www.furyosociety.com"
    }

    deeplink {
        host("www.furyosociety.com")
        host("furyosociety.com")
        path("/series/.*")
        path("/read/.*")
    }
}
