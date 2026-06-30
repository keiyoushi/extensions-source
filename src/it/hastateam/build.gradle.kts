plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hasta Team"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "pizzareader"

    source {
        lang = "it"
        baseUrl = "https://reader.hastateam.com"
    }
}
