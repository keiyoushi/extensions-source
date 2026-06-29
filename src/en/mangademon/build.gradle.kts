plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Demon"
    versionCode = 19
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://demonicscans.org"
        versionId = 2
    }
}
