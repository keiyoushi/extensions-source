plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangamo"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.mangamo.com"
    }
}
