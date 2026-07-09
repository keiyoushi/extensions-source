plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Bahçesi"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://mangabahcesi.com"
        id = 7110025728969951060L
    }
}
