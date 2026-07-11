plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MilaSub"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://www.millascan.com"
    }
}
