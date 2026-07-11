plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangoLibreria"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://mangolibreria.com"
        versionId = 2
    }
}
