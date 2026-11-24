import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dong Manga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "ดองมังงะ"
        lang = "th"
        baseUrl = "https://dongmanga.com"
    }

    deeplink {
        path("/forum-..*")
    }
}
