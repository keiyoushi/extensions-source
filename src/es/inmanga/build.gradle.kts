plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "InManga"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://inmanga.com"
    }
}
