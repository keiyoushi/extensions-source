plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "8Muses"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "eromuse"

    source {
        lang = "en"
        baseUrl = "https://comics.8muses.com"
    }
}
