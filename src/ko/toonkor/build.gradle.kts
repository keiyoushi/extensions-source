plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonkor"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ko"
        baseUrl("https://tkor131.com") {
            withCustom = true
        }
    }
}
