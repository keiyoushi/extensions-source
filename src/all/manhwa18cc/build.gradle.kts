plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa18.cc"
    versionCode = 7
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    listOf("en", "ko", "all").forEach {
        source {
            lang = it
            baseUrl = "https://manhwa18.cc"
        }
    }
}
