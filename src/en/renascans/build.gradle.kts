import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Renascans"
    versionCode = 30
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://renascans.net"
        lang = "en"
    }

    deeplink {
        path("/manga/..*")
        path("/series/..*")
    }
}
