plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Murim"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "ar"
        baseUrl = "https://www.murim.site"
    }
}
