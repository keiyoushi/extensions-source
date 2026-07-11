plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Scan Reader"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "scanreader"

    source {
        lang = "fr"
        baseUrl = "https://hentai.scanreader.net"
    }
}
