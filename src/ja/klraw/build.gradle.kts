plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KL Raw"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangareader"

    source {
        lang = "ja"
        baseUrl = "https://www.klraw.info"
    }
}
