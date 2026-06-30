plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dokiraw"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "liliana"

    source {
        lang = "ja"
        baseUrl = "https://dokiraw.cloud"
    }
}
