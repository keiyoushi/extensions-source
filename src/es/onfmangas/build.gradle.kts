plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ONF MANGAS"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://onfmangas.com"
    }
}
