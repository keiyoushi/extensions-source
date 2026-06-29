plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Noblesse Translations"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl("https://nobledicion.yoveo.xyz") {
            withCustom = true
        }
    }
}
