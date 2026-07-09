plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KamiComic"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://kamicomi.com"
    }
}
