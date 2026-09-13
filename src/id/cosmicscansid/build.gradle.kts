import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CosmicScans"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://04.cosmicscans.to"
        id = 6559481336553833282L
    }

    deeplink {
        path("/series/..*")
        path("/manga/..*")
    }
}
