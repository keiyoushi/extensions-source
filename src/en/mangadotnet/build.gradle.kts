plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangadotnet"
    versionCode = 13
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
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
