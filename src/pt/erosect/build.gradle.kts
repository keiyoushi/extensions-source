plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ero Sect"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "EroSect"
        lang = "pt-BR"
        baseUrl = "https://erosect.xyz"
    }
}
