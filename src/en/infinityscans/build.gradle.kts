plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "InfinityScans"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://infinityscans.org"
        versionId = 2
    }
}
