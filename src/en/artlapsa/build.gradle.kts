import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Art Lapsa"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://artlapsa.com"
    }
}
