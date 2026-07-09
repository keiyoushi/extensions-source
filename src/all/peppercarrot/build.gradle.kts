plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pepper&Carrot"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://www.peppercarrot.com"
    }
}
