plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Top Manhua"
    versionCode = 7
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://mangatop.org"
    }
}
