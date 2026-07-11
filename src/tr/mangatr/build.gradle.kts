plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-TR"
    versionCode = 23
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://manga-tr.com"
    }
}
