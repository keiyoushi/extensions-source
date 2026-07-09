plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zinmanga.net"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://zinmanga.net"
    }
}
