plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LectorJPG"
    versionCode = 50
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://visorjpg.lat"
        versionId = 3
    }
}
