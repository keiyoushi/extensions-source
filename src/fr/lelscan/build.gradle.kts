plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lelscan"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://lelscans.net"
    }
}
