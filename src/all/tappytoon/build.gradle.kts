plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tappytoon"
    versionCode = 10
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    listOf("en", "fr", "de").forEach {
        source {
            lang = it
            baseUrl = "https://www.tappytoon.com/$it"
        }
    }
}
