plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangakakalot"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangabox"

    source {
        lang = "en"
        baseUrl("https://www.mangakakalot.gg") {
            mirrors = listOf(
                "https://www.mangakakalove.com",
            )
        }
    }
}
