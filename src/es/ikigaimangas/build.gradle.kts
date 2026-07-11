plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikigai Mangas"
    versionCode = 34
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl {
            custom("https://zonaikigai.gamesview.shop")
        }
        versionId = 2
    }
}
