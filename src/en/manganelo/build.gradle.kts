plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manganato"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangabox"

    source {
        lang = "en"
        baseUrl("https://www.natomanga.com") {
            mirrors = listOf(
                "https://www.nelomanga.com",
                "https://www.nelomanga.net",
                "https://www.manganato.gg",
            )
        }
        id = 1024627298672457456L
    }
}
