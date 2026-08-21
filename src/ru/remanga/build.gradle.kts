import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReManga"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "ReManga"
        lang = "ru"
        baseUrl = "https://remanga.org"
    }

    deeplink {
        path("/titles/..*")
        path("/manga/..*")
    }
}
