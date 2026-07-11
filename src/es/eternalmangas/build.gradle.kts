plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "EternalMangas"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "iken"

    source {
        baseUrl = "https://eternalmangas.org"
        lang = "es"
    }
}
