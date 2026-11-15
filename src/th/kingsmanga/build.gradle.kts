import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KingsManga"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://www.kingsmanga.net"
    }

    deeplink {
        host("www.kingsmanga.net")
        host("kingsmanga.net")
        path("/..*")
    }
}
