plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangitto"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://mangtto.com"
    }
}
