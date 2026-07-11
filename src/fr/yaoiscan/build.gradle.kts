plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiScan"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "fr"
        baseUrl = "https://yaoiscan.fr"
    }
}
