import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NIFTeam"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "foolslide"

    source {
        lang = "it"
        baseUrl = "https://read-nifteam.info"
    }
}
