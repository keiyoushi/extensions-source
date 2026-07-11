plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Geass Comics"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://geasscomics.xyz"
        versionId = 2
    }
}
