plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangadusleri"
    versionCode = 33
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        versionId = 2
        lang = "tr"
        baseUrl {
            custom("https://mangadusleri.mom")
        }
    }
}
