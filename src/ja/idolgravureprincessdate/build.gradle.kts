plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Idol. gravureprincess .date"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://idol.gravureprincess.date"
    }
}
