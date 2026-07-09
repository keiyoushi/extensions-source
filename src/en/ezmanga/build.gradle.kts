plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "EZmanga"
    versionCode = 60
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "ezmanhwa"

    source {
        lang = "en"
        baseUrl = "https://ezmanga.org"
        versionId = 5
    }
}
