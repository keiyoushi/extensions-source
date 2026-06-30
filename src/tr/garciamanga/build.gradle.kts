plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Garcia Manga"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://garciamanga.com"
    }
}
