plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komik Dewasa"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        name = "Komik Dewasak"
        lang = "id"
        baseUrl = "https://komikdewasa.mom"
    }
}
