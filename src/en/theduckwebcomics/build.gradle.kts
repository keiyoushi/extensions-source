plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Duck Webcomics"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.theduckwebcomics.com"
    }
}
