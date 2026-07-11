import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "InfinityScans"
    versionCode = 10
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://infinityscans.org"
        versionId = 2
    }
}
