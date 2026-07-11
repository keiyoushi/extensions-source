plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hennojin"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    listOf("en", "ja").forEach {
        source {
            lang = it
            baseUrl = "https://hennojin.com"
        }
    }
}
