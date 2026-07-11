plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sen Manga"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://raw.senmanga.com"
    }
}
