plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuaren"
    versionCode = 18
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "漫画人"
        lang = "zh"
        baseUrl = "http://mangaapi.manhuaren.com"
    }
}
