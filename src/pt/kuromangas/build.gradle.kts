plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KuroMangas"
    versionCode = 9
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://kuromangas.com"
    }
}
