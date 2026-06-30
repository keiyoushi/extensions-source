plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Monopoly Scan"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://monopolymanhua.com"
    }
}
