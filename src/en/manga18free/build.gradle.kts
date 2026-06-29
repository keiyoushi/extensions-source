plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga18Free"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manga18free.com"
    }
}
