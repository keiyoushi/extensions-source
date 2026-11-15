import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zenko"
    versionCode = 9
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "uk"
        baseUrl = "https://zenko.online"
    }

    deeplink {
        path("/titles/..*")
    }
}
