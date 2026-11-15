import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HOMOcdrs"
    theme = "zeistmanga"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://homocdrscan.blogspot.com"
    }

    deeplink {
        path("/search/label/..*")
    }
}
