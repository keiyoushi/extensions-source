plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Argos Comics"
    versionCode = 55
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://aniargos.com"
        versionId = 2
    }
}
