plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WoopRead"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://woopread.com"
    }
}
