plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadManga"
    versionCode = 48
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl("https://a.zazaza.me") {
            withCustom = true
        }
        lang = "ru"
        id = 5L
    }
}
