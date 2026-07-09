plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "El Goonish Shive"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.egscomics.com"
    }
}
