plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hanmokku Scan"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://hanmokkuscan.blogspot.com"
    }
}
