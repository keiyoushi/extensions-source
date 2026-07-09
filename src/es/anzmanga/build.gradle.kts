plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AnzManga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://www.anzmanga25.com"
    }
}
