plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AsmHentai"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "galleryadults"

    listOf("en", "ja", "zh", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://asmhentai.com"
        }
    }
}
