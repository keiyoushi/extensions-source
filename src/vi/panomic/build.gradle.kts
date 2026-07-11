import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Panomic"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://panomic1.info"
    }
}
