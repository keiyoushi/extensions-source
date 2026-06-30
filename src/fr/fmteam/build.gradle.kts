plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FMTEAM"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "pizzareader"

    source {
        lang = "fr"
        baseUrl = "https://fmteam.fr"
        versionId = 2
    }
}
