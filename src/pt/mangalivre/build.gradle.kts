plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Livre"
    versionCode = 71
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        baseUrl = "https://toonlivre.net"
        lang = "pt-BR"
        versionId = 2
    }
}
