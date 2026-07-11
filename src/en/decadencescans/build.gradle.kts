plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Decadence Scans"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://reader.decadencescans.com"
    }
}
