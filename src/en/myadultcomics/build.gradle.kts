plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyAdultComics"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://myadultcomics.com"
    }
}
