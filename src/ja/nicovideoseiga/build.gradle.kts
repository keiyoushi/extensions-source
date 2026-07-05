plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nicovideo Seiga"
    versionCode = 9
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://sp.manga.nicovideo.jp"
        versionId = 2
    }
}
