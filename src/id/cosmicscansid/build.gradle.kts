import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CosmicScans"
    versionCode = 56
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://02.cosmicscans.to"
        id = 6559481336553833282L
    }
}
