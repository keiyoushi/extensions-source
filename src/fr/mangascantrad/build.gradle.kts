plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-Scantrad"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://manga-scantrad.io"
    }
}
