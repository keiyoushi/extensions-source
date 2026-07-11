plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doujins"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://doujins.com"
    }
}
