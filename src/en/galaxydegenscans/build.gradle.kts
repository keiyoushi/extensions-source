plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GalaxyDegenScans"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://gdscans.com"
    }
}
