plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lily Manga"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://lilymanga.net"
    }
}
