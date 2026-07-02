plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Miau Scan"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    listOf("es", "pt-BR").forEach { sourceLang ->
        source {
            lang = sourceLang
            baseUrl = "https://leemiau.com"
        }
    }
}
