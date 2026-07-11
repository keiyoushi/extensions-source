plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaBTT"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://manhwabtt.cc"
    }
}
