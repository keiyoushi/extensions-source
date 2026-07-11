plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "X-Manga"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://x-manga.org"
        id = 4153742697148883998L
    }
}
