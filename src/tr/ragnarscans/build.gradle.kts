plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ragnar Scans"
    versionCode = 46
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "initmanga"

    source {
        lang = "tr"
        baseUrl = "https://ragnarscans.net"
        versionId = 2
    }
}
