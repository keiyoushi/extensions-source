plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaZure"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://mangazure.net"
    }
}
