plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "JJCOS"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://jjcos.com"
    }
}
