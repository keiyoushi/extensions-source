plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bakai"
    versionCode = 16
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://bakai.org"
    }
}
