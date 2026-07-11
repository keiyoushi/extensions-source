plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-Lc"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "th"
        baseUrl = "https://manga-lc.net"
    }
}
