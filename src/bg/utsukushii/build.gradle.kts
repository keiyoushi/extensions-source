import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Utsukushii"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mmrcms"

    source {
        lang = "bg"
        baseUrl = "https://utsukushii-bg.com"
    }
}
