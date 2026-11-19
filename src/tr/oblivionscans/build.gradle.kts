import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Oblivion Scans"
    theme = "duckscans"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://oblivionscans.com"
    }
}
