plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "EnchiladaScan"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://enchiladascan.github.io/enchiladaweb"
    }
}
