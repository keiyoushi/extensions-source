plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-Park"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://manga-park.com"
    }
}
