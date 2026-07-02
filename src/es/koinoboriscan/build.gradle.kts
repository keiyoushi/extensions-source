plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Koinobori Scan"
    versionCode = 40
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://visorkoi.com"
        versionId = 3
    }
}
