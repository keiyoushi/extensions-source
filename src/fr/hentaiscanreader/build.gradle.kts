plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Scan Reader"
    className = "HentaiScanReader"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "scanreader"
    baseUrl = "https://hentai.scanreader.net"
}
