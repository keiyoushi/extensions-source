import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ragnar Scans"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "initmanga"

    source {
        lang = "tr"
        baseUrl = "https://ragnarscans.net"
        versionId = 2
    }
}
