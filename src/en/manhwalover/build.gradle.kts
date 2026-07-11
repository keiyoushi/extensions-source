plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwalover"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://www.manhwalover.org"
    }
}
