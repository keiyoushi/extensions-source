plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HANMAN18"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "manga18"

    source {
        lang = "zh"
        baseUrl = "https://hanman18.com"
    }
}
