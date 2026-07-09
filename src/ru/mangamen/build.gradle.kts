plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaMen"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://mangamen.com"
    }
}
