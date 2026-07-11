plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pantheon Scan"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://pantheon-scan.com"
    }
}
