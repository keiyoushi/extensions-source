plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KingComiX"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://kingcomix.com"
    }
}
