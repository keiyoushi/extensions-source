plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MurimScan"
    versionCode = 36
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "en"
        baseUrl = "https://www.murimscans.site"
        // Madara -> ZeistManga
        versionId = 2
    }
}
