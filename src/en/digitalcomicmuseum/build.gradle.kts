plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Digital Comic Museum"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://digitalcomicmuseum.com"
    }
}
