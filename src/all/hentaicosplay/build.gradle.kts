plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Cosplay"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://hentai-cosplay-xxx.com"
    }
}
