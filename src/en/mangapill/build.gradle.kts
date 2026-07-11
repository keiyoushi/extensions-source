plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaPill"
    versionCode = 9
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangapill.com"
    }
}
