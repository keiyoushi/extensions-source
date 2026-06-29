plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Twicomi"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://twicomi.com"
    }
}
