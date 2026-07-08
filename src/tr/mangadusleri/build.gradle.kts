plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangadusleri"
    versionCode = 33
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl {
            custom("https://mangadusleri.mom")
        }
    }
}
