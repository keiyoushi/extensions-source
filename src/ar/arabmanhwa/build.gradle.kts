plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ArabManhwa"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "ArabManhwa"
        lang = "ar"
        baseUrl = "https://arabmanhwa.com"
    }
}
