plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shadow Manga"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://shademanga.com"
    }
}
