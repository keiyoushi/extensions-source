plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DailySuka"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        name = "DailySuka "
        lang = "id"
        baseUrl = "https://dailysuka.com"
    }
}
