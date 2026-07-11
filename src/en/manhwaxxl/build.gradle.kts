plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa XXL"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hentaitnt.net"
        versionId = 2
    }
}
