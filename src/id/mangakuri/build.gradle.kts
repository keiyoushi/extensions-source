plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangakuri"
    versionCode = 33
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://lc1.mangakuri.online"
        versionId = 2
    }
}
