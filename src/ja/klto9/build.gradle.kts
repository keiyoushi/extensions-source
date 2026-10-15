import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Klto9"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://klto9.com"
    }

    deeplink {
        path("/teap-..*\\.html")
        path("/zmqs-..*\\.html")
    }
}
