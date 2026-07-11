plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WeLoveManga"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "fmreader"

    source {
        lang = "ja"
        baseUrl = "https://weloma.art"
        // Formerly "RawLH"
        id = 7595224096258102519L
    }
}
