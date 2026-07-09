plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Eggporncomics"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://eggporncomics.com"
    }
}
