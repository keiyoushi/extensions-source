plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaTaro"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangataro"

    listOf("en", "pt-BR").forEach { language ->
        source {
            lang = language
            baseUrl = "https://mangataro.org"
        }
    }
}
