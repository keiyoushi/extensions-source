import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KeroScans"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://keroscans.net"
        lang = "en"
    }

    deeplink {
        path("/..*")
    }
}
