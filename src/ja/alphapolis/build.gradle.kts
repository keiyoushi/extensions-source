import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Alphapolis"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://www.alphapolis.co.jp"
        versionId = 2
    }
}
