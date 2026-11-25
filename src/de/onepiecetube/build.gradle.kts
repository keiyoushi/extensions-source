import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OnePiece-Tube"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "de"
        baseUrl = "https://onepiece.tube"
    }

    deeplink {
        host("onepiece.tube")
        host("onepiece-tube.com")
        host("www.onepiece-tube.com")
        path("/manga/..*")
    }
}
