import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaTek"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ar"
        baseUrl = "https://mangatek.com"
    }
}
