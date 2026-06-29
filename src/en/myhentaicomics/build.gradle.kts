plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyHentaiComics"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://myhentaicomics.com"
    }
}
