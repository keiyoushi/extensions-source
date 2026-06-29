plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Revistas e Quadrinhos"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "pt"
        baseUrl = "https://revistasequadrinhos.com"
    }
}
