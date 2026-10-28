import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Softkomik"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://softkomik.app"
    }
}
