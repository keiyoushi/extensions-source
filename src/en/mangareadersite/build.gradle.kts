plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaReader.site"
    className = "MangaReaderSite"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangahub"
    baseUrl = "https://mangareader.site"
}
