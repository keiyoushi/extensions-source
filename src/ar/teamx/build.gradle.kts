plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Team X"
    className = "TeamX"
    versionCode = 30
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("olympustaff.com")
        path("/series/..*")
    }
}
