plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaTaro"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangataro"

    source {
        lang = "en"
        baseUrl = "https://mangataro.org"
    }
}
