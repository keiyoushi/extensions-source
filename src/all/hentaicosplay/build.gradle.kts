plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Cosplay"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://hentai-cosplay-xxx.com"
    }
}
