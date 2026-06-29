plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaMeets"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://manga-meets.jp"
    }
}
