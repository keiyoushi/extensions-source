import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hibon BL Scanlation"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "es"
        baseUrl = "https://hibon-bl.blogspot.com"
    }

    deeplink {
        path("/search/label/..*")
    }
}
