plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "J-N Books"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://comic.j-nbooks.jp"
    }
}
