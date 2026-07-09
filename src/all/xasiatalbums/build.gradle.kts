plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Xasiat Albums"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "XAsiat Albums"
        lang = "all"
        baseUrl = "https://www.xasiat.com"
    }
}
