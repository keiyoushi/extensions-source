plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Blackout Comics"
    versionCode = 10
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://blackoutcomics.com"
    }
}
