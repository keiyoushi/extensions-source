plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhuaRock"
    versionCode = 18
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://manhuarock4.site") {
            withCustom = true
        }
        versionId = 2
    }
}
