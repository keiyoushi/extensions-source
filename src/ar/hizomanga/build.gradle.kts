plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HizoManga"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "Hizo Manga"
        lang = "ar"
        baseUrl = "https://hizomanga.net"
    }
}
