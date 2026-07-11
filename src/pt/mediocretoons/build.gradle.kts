plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mediocre Toons"
    versionCode = 20
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://mediocrescan.com"
    }
}
