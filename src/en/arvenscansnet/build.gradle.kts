import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arven Scans"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://arvenscans.net"
        versionId = 2
    }

    deeplink {
        host("arvenscans.net")
        host("www.arvenscans.net")
        path("/series/..*")
        path("/reader/..*")
    }
}
