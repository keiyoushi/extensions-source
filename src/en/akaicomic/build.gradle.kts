plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Akai Comic"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://akaicomic.org"
    }
}
