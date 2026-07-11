plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bbato"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://bbato.com"
    }
}
