plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Twicomi"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://twicomi.com"
    }
}
