plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDar"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ar"
        baseUrl = "https://mangadar.com"
        skipCodeGen.set(true)
    }
}
