plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MomonGA"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "momon:GA"
        lang = "ja"
        baseUrl = "https://momon-ga.com"
    }
}
