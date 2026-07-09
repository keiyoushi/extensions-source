plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Saikai Scan"
    versionCode = 13
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://housesaikai.net"
    }
}
