plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ragnarok Scanlation"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://ragnarokscanlation.org"
        versionId = 2
    }
}
