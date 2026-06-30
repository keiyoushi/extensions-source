plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Spicy Scan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "spicytheme"

    source {
        lang = "es"
        baseUrl = "https://spicyseries.com"
    }
}
