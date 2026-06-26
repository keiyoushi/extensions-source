plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FuryoSquad"
    className = "FuryoSquad"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("www.furyosociety.com")
        host("furyosociety.com")
        path("/series/.*")
        path("/read/.*")
    }
}
