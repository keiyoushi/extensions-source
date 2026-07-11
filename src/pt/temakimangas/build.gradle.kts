plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Temaki mangás"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "pt-BR"
        baseUrl = "https://temakimangas.blogspot.com"
    }
}
