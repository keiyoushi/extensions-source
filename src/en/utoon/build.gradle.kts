import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Utoon"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://utoon.net"
    }
}
