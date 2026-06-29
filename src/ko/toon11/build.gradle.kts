plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "11toon"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ko"
        baseUrl = "https://www.11toon.com"
    }
}
