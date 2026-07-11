plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zenko"
    versionCode = 7
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "uk"
        baseUrl = "https://zenko.online"
    }
}
