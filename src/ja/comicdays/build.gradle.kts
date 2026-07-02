plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Days"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "gigaviewer"

    source {
        lang = "ja"
        baseUrl = "https://comic-days.com"
    }
}
