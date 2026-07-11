plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDNA"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    listOf("en", "all").forEach {
        source {
            lang = it
            baseUrl = "https://mangadna.com"
        }
    }
}
