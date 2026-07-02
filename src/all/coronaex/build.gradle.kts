plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Corona EX"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://to-corona-ex.com"
    }
    source {
        lang = "en"
        baseUrl = "https://en.to-corona-ex.com"
    }
}
