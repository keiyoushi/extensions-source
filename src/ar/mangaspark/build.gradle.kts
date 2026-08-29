import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaSpark"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "ar"
        baseUrl = "https://sparkmanga.net"
    }
}
