plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comicabc"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "無限動漫"
        lang = "zh"
        baseUrl = "https://www.8comic.com"
    }
}
