plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RawBaka"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ja"
        baseUrl = "https://rawbaka.com"
    }
}
