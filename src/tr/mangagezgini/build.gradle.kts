plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaGezgini"
    versionCode = 9
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://mangagezgini.online"
    }
}
