plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonkor"
    versionCode = 7
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ko"
        baseUrl {
            custom("https://tkor131.com")
        }
    }
}
