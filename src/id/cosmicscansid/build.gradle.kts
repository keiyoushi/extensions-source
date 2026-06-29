plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CosmicScansID"
    versionCode = 55
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://01.cosmicscans.to"
        versionId = 1
    }
}
