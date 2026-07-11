plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Temple Scan"
    versionCode = 12
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl {
            custom("https://aedexnox.akan01.com")
        }
        versionId = 4
    }
}
