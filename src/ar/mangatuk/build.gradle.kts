plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaTuk"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl = "https://mangatuk.com"
    }
}
