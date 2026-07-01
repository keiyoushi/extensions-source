plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaSwat"
    versionCode = 61
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ar"
        versionId = 2
        baseUrl("https://meshmanga.com") {
            withCustom = true
        }
    }
}
