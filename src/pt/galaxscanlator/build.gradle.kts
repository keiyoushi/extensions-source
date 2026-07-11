plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GALAX Scans"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://galaxscanlator.blogspot.com"
    }
}
