plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TheManga"
    versionCode = 49
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://themanga.site"
    }
}
