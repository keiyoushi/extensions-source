import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ScansFR"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://scansfr.com"
    }
}
