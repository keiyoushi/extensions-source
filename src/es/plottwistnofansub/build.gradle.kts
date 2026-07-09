plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Plot Twist No Fansub"
    versionCode = 15
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://plotnofansub.com"
    }
}
