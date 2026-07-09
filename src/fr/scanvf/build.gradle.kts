plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Scan VF"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mmrcms"

    source {
        lang = "fr"
        baseUrl = "https://www.scan-vf.net"
    }
}
