plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-Scantrad"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://manga-scantrad.io"
    }
}
