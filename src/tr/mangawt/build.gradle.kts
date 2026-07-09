plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaWT"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://mangawt.com"
    }
}
