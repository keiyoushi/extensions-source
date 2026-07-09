plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MAGCOMI"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "gigaviewer"

    source {
        lang = "ja"
        baseUrl = "https://magcomi.com"
    }
}
