plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fenix Project"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://fenixproject.site"
    }
}
