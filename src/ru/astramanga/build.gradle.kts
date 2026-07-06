plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AstraManga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://astramanga.org"
    }
}
