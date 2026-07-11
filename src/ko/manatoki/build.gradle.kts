plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manatoki"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ko"
        baseUrl = "https://manatoki552.net"
        versionId = 2
    }
}
