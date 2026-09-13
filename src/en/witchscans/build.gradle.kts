import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WitchScans"
    versionCode = 32
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "vinetheme"

    source {
        lang = "en"
        baseUrl = "https://witchtoons.net"
        versionId = 2
    }
}
