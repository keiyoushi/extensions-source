plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Domal Fansub"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://dom4lfansub.online"
    }
}
