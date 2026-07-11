plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KomikIndoID"
    versionCode = 19
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://komikindo.ch"
    }
}
