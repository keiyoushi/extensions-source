plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaFox"
    versionCode = 9
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://fanfox.net"
    }
}
