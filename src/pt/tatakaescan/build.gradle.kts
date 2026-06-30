plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tatakae Scan"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://tatakaescan.com"
    }
}
