plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Catharsis World"
    versionCode = 14
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl {
            custom("https://catharsisworld.dig-it.info")
        }
        versionId = 2
    }
}
