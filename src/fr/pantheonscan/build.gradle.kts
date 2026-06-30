plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pantheon Scan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://pantheon-scan.com"
    }
}
