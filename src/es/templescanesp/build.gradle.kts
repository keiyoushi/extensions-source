plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Temple Scan"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl("https://aedexnox.akan01.com") {
            withCustom = true
        }
        versionId = 4
    }
}
