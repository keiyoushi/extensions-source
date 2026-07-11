plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaReader.in"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangareader.in"
        id = 7388100486112484697L
    }
}
