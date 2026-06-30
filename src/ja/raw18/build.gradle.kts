plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Raw18"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "ja"
        baseUrl = "https://raw18.cool"
    }
}
