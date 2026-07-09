plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ler 999"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://ler999.blogspot.com"
    }
}
