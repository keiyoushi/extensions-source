plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaOwl.io (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://mangaowl.io"
    }
}
