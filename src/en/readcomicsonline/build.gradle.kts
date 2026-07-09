plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Comics Online"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mmrcms"

    source {
        lang = "en"
        baseUrl = "https://readcomicsonline.ru"
    }
}
