plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Line Manga"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://manga.line.me"
    }
}
