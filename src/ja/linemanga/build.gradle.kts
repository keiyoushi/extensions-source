plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Line Manga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://manga.line.me"
    }
}
