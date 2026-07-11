plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Maid - Manga"
    versionCode = 10
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zmanga"

    source {
        lang = "id"
        baseUrl = "https://www.maid.my.id"
    }
}
