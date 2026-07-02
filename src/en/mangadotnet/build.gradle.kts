plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangadotnet"
    versionCode = 13
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangadot.net"
    }

    deeplink {
        host("mangadot.net")
        path("/manga/..*")
        path("/chapter/..*")
    }
}
