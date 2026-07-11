plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga18Me"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    listOf("all", "en").forEach {
        source {
            name = "Manga18.me"
            lang = it
            baseUrl = "https://manga18.me"
        }
    }
}
