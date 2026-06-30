plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rackus"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://rackusreads.com"
        versionId = 3
    }
}
