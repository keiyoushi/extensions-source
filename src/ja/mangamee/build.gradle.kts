plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaMee"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://manga-mee.jp"
    }
}
