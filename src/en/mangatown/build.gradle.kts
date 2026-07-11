plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangatown"
    versionCode = 10
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.mangatown.com"
    }
}
