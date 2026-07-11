plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comics Valley"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "all"
        baseUrl = "https://comicsvalley.com"
        id = 1103204227230640533L
    }
}
