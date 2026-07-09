plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ragna Scans"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://lector.ragnascan.xyz"
    }
}
