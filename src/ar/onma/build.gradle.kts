import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Onma"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mmrcms"

    source {
        name = "مانجا اون لاين"
        lang = "ar"
        baseUrl = "https://onma.top"
    }
}
