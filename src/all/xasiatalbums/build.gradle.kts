plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Xasiat Albums"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "XAsiat Albums"
        lang = "all"
        baseUrl = "https://www.xasiat.com"
    }
}
