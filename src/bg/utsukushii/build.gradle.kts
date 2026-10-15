import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Utsukushii"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mmrcms"

    source {
        lang = "bg"
        baseUrl = "https://utsukushii-bg.com"
    }
}
