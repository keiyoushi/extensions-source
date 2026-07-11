plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kemono"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "kemono"

    source {
        lang = "all"
        baseUrl = "https://kemono.cr"
    }
}
