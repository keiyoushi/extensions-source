plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Renascans"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "iken"

    source {
        baseUrl = "https://renascans.net"
        lang = "en"
    }
}
