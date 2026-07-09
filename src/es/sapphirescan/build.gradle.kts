plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SapphireScan"
    versionCode = 40
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "es"
        baseUrl = "https://www.sapphirescan.com"
    }
}
