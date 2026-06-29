plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Corocoro Online"
    className = "CorocoroOnline"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://www.corocoro.jp"
        versionId = 3
    }
}
