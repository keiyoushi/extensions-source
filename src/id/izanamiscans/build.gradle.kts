plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Izanami Scans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://izanamiscans.my.id"
    }
}
