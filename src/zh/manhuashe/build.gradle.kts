plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuashe"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "漫画社"
        lang = "zh"
        baseUrl = "https://www.311s.com"
    }
}
