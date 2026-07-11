plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ego Toons"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://www.egotoons.com"
        versionId = 3
    }
}
