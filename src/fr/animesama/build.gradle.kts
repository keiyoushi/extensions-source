plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AnimeSama"
    className = "AnimeSama"
    versionCode = 17
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("anime-sama.to")
        path("/catalogue/..*")
    }
}
