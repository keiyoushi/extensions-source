plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaNex"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manhwanex.com"
    }
}
