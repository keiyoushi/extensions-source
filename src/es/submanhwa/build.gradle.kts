plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Submanhwa"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://submanhwa.com"
    }
}
