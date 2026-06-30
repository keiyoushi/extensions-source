plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Adonis Fansub"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://manga.adonisfansub.com"
    }
}
