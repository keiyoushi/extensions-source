plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikigai Mangas"
    versionCode = 34
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl("https://zonaikigai.gamesview.shop") {
            withCustom = true
        }
        versionId = 2
    }
}
