plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaDesu"
    versionCode = 11
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://manhwadesu.store"
    }
}
