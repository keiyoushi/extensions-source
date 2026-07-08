plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manwa"
    versionCode = 14
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "漫蛙"
        lang = "zh"
        baseUrl = "https://manwa.me"
    }
}
