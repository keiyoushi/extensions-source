plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaTilkisi"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://www.tilkiscans.com"
    }
}
