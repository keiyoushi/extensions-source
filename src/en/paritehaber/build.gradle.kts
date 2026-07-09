plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Paritehaber"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://www.paritehaber.com"
    }
}
