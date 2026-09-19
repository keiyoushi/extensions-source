import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

dependencies {
    implementation(project(":lib:randomua"))
}

keiyoushi {
    name = "MangaSpark"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "ar"
        baseUrl = "https://sparkmanga.net"
    }
}
