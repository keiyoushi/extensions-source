plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Biblio Panda"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://bibliopanda.com"
    }
}
