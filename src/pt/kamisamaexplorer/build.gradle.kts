plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kami Sama Explorer"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://leitor.kamisama.com.br"
    }
}
