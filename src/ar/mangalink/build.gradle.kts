plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangalink"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "مانجا لينك"
        lang = "ar"
        baseUrl {
            custom("https://link-manga.net")
        }
    }
}
