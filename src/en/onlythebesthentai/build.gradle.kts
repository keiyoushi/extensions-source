plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Only The Best Hentai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://onlythebesthentai.com"
    }
}
