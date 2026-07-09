plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TakeComic"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://takecomic.jp"
    }
}
