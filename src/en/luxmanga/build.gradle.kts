import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LuxManga"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://luxmanga.org"
        lang = "en"
        versionId = 2
    }

    deeplink {
        path("/series/..*")
        path("/reader/..*")
    }
}
