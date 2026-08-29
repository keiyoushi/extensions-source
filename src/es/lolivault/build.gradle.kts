import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lolivault"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "foolslide"

    source {
        lang = "es"
        baseUrl = "https://lector.lolivault.net"
    }
}
