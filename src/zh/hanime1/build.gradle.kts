plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hanime1"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "zh"
        name = "Hanime1.me"
        baseUrl = "https://hanimeone.me"
    }
}
