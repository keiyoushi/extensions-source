plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Catharsis World"
    versionCode = 14
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl("https://catharsisworld.dig-it.info") {
            withCustom = true
        }
        versionId = 2
    }
}
