plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Champion Cross"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://championcross.jp"
        versionId = 2
    }
}
