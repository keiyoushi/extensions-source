plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Epsilon Scan"
    versionCode = 52
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "pam"

    source {
        lang = "fr"
        baseUrl = "https://epsilonscan.to"
        versionId = 2
    }
}
