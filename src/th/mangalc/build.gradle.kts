plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-Lc"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "th"
        baseUrl = "https://manga-lc.net"
    }
}
