plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Scan Reader"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "scanreader"

    source {
        lang = "fr"
        baseUrl = "https://scanreader.net"
    }
}
