plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Les Poroiniens"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://lesporoiniens.org"
    }
}
