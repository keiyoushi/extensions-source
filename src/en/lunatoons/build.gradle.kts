import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Luna Toons"
    versionCode = 22
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "keyoappv2"

    source {
        lang = "en"
        baseUrl = "https://lunatoons.net"
        versionId = 2
    }
}
