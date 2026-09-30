import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZeurelScan"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "it"
        baseUrl = "https://www.zeurelscan.com"
    }
}
