plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AnimeSama"
    versionCode = 17
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://anime-sama.to"
    }

    deeplink {
        host("anime-sama.to")
        path("/catalogue/..*")
    }
}
