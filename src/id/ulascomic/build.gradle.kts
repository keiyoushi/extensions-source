plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ulas Comic"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "https://www.ulascomic01.xyz"
    }
}
