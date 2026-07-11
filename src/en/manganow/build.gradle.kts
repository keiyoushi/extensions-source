plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaNow"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangareader"

    source {
        lang = "en"
        baseUrl = "https://manganow.to"
    }
}
