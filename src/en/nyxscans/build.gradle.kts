plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nyx Scans"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "iken"

    source {
        baseUrl = "https://nyxmanga.org"
        lang = "en"
    }
}
