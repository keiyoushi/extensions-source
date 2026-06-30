plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rocks Manga"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl = "https://rocksmanga.com"
    }
}
