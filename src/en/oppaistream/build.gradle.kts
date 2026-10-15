import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Oppai Stream"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://read.oppai.stream"
    }

    deeplink {
        host("read.oppai.stream")
        path("/manhwa")
        path("/page")
    }
}
