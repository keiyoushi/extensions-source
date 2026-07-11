plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NhentaiClub"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://nhentaiclub.space"
        id = 9124366814387777661L
    }
}
