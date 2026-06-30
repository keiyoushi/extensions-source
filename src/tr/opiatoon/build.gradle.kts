plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Opiatoon"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://opiatoon.shop"
    }
}
