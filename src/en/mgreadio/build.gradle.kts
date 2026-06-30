plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mgread.io"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mgread.io"
    }
}
