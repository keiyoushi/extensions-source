plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwalike"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://manhwalike.com"
    }
}
