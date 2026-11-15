import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LuxManga"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://luxmanga.net"
        lang = "en"
        versionId = 2
    }

    deeplink {
        path("/series/..*")
        path("/reader/..*")
    }
}
