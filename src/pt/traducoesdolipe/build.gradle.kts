plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Traduções do Lipe"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://traducoesdolipe.blogspot.com"
    }
}
