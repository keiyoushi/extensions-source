import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DGManga"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "uk"
        baseUrl = "https://dgmanga.app"
        versionId = 2
    }

    deeplink {
        path("/title/..*")
    }
}
