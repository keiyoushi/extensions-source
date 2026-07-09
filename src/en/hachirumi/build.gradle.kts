plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hachirumi"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "guya"

    source {
        lang = "en"
        baseUrl = "https://hachirumi.com"
    }
}
