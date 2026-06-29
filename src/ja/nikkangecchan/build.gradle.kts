plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nikkangecchan"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://nikkangecchan.jp"
    }
}
