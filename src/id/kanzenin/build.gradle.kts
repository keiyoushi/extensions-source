plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kanzenin"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://kanzenin.info"
    }
}
