plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Plot Twist No Fansub"
    versionCode = 15
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://plotnofansub.com"
    }
}
