plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Oppai Stream"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

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
