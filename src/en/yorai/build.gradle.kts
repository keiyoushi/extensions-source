import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yorai"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://yorai.io"
        versionId = 2
    }
}
