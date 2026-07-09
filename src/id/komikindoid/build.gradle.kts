plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KomikIndoID"
    versionCode = 19
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://komikindo.ch"
    }
}
