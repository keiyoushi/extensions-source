plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OkyyKomik"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "http://www.okyykomik.my.id"
    }
}
