plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ScansGG"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://scans.gg"
    }
}
