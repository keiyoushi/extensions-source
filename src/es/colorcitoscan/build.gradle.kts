plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Colorcito Scan"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "spicytheme"

    source {
        lang = "es"
        baseUrl = "https://colorcitoscan.com"
    }
}
