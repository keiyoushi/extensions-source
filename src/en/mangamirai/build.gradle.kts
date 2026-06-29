plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Mirai"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangamirai.com"
    }
}
