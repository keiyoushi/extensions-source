plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Magazine Pocket"
    versionCode = 12
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://pocket.shonenmagazine.com"
        versionId = 2
    }
}
