plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Starz"
    versionCode = 9
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl = "https://manga-starz.net"
    }
}
