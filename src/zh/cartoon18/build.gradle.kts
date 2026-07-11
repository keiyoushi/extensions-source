plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cartoon18"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl = "https://www.cartoon18.com"
    }
}
