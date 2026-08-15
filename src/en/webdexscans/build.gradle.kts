import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webdex Scans"
    versionCode = 54
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://webdexscans.com"
        // Bump versionId to force entry migration as URL fields changed to UUIDs.
        versionId = 3
    }

    deeplink {
        path("/series/..*")
    }
}
