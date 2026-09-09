import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Blossom Scans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://blossom-scans.com"
        lang = "fr"
    }

    deeplink {
        path("/serie/..*")
    }
}
