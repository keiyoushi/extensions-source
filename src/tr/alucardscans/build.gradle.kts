import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Alucard Scans"
    versionCode = 31
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://alucardscans.com"
        versionId = 2
    }
}
