plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Ride"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://comicride.jp"
    }
}
