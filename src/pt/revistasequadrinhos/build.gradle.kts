plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Revistas e Quadrinhos"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt"
        baseUrl = "https://revistasequadrinhos.com"
    }
}
