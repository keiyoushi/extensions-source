plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LectorJPG"
    versionCode = 50
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://visorjpg.lat"
        versionId = 3
    }
}
