plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Corocoro Online"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://www.corocoro.jp"
        versionId = 2
    }
}
