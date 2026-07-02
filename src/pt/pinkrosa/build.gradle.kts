plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pink Rosa"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://scanpinkrosa.blogspot.com"
    }
}
