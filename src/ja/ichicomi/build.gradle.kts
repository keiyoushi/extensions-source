plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ichicomi"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "gigaviewer"

    source {
        lang = "ja"
        baseUrl = "https://ichicomi.com"
    }
}
