plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Vortex Scans"
    versionCode = 61
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://vortexscans.org"
    }

    deeplink {
        host("vortexscans.org")
        path("/series/..*")
    }
}
