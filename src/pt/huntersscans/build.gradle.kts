plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hunters Scans"
    versionCode = 11
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "Hunters Scan"
        lang = "pt-BR"
        baseUrl = "https://readhunters.xyz"
    }
}
