plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Tube"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "de"
        baseUrl = "https://manga-tube.me"
    }
}
