plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FoolSlide Customizable"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "foolslide"

    source {
        lang = "other"
        baseUrl {
            custom("https://127.0.0.1")
        }
    }
}
