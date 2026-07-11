plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "House Of Otakus"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://houseofotakusv2.xyz"
    }
}
