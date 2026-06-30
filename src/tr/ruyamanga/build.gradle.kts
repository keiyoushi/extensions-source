plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rüya Manga"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://www.ruyamanga2.com"
    }
}
