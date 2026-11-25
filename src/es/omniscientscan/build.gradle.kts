import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Omniscient Scan"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://omniscan.site"
    }

    deeplink {
        host("omniscan.site")
        host("www.omniscan.site")
        path("/obra/..*")
        path("/biblioteca")
    }
}
