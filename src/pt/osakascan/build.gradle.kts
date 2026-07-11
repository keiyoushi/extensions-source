plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Osaka Scan"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://www.osakascan.com"
    }
}
