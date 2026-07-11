plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Everia.club"
    versionCode = 12
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://everia.club"
    }
}
