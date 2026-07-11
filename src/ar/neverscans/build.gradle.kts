import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NeverScans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ar"
        baseUrl = "https://neverscans.com"
    }
}
