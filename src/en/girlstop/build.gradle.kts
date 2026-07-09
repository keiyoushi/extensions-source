plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GirlsTop"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://en.girlstop.info"
    }
}
