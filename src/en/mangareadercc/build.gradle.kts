plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaReader.in"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "paprika"

    source {
        lang = "en"
        baseUrl = "https://mangareader.in"
        id = 7388100486112484697L
    }
}
