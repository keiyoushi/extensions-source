import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RF Dragon Scan"
    versionCode = 12
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://rfdragonscan.net"
    }
}
